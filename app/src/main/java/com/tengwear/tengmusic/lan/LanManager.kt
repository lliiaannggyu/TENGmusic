package com.tengwear.tengmusic.lan

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.tengwear.tengmusic.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 局域网功能总入口（进程内单例）：
 *  - 发现：[peers] 实时反映同一 Wi-Fi 下的对端设备
 *  - 传歌：把本地曲目文件推给对端，落对方媒体库（可配阈值，超限先压缩）
 *  - 一起听：把当前歌曲+歌词推给对端，落对方缓存目录，退出即删不留残余
 *  - 接收：对端连接后先弹「待决定」事件，用户接受才真正收字节
 *
 * 所有 socket / 文件 IO 均在 Dispatchers.IO；UI 只跟 [peers] / [transfers] / [events] 打交道。
 */
object LanManager {

    private const val TAG = "LanManager"
    private const val LISTEN_DIR = "listen_together"   // cacheDir 下，退出一起听即整体删除
    private const val TMP_DIR = "lan_tmp"              // 传输临时目录，启动时清残留

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var ctx: Context? = null
    private var started = false
    private var myId: String = UUID.randomUUID().toString()
    private var myName: String = "TENGMusic"

    private var discovery: LanDiscovery? = null
    private var server: LanServer? = null

    /** 待用户决定的传入请求：transferId → 是否接受 */
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private val _peers = MutableStateFlow<List<LanPeer>>(emptyList())
    val peers: StateFlow<List<LanPeer>> = _peers.asStateFlow()

    /**
     * 待决定的传入请求（StateFlow 而非一次性事件：UI 可能在事件抛出后才订阅，
     * 例如 WearApp 收到事件才打开对应页面，用状态承载不会丢）。
     */
    private val _pendingSongOffer = MutableStateFlow<LanEvent.SongOffer?>(null)
    val pendingSongOffer: StateFlow<LanEvent.SongOffer?> = _pendingSongOffer.asStateFlow()

    private val _pendingListenOffer = MutableStateFlow<LanEvent.ListenOffer?>(null)
    val pendingListenOffer: StateFlow<LanEvent.ListenOffer?> = _pendingListenOffer.asStateFlow()

    private val _transfers = MutableStateFlow<List<TransferState>>(emptyList())
    val transfers: StateFlow<List<TransferState>> = _transfers.asStateFlow()

    private val _events = MutableSharedFlow<LanEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<LanEvent> = _events.asSharedFlow()

    /**
     * 一起听「控制命令」流（解析后供 ViewModel 应用）：主播周期下发 / 事件下发的
     * [State]（idx/pos/playing），以及结束会话的 [Stop]。仅嘉宾侧消费。
     */
    private val _controlCommands = MutableSharedFlow<LanControlCommand>(extraBufferCapacity = 32)
    val controlCommands: SharedFlow<LanControlCommand> = _controlCommands.asSharedFlow()

    /** 嘉宾端已落盘、按推送顺序排列的临时歌单（索引与主播歌单严格对齐） */
    private val _listenSongs = MutableStateFlow<List<SharedSong>>(emptyList())
    val listenSongs: StateFlow<List<SharedSong>> = _listenSongs.asStateFlow()

    /** 主播端当前会话（持有持久控制连接）；为 null 表示未在进行一起听 */
    private var leaderSession: LeaderSession? = null

    /** 主播端临时歌单缓存文件 + 元信息 */
    data class SharedSong(val meta: SharedMeta, val file: File)

    /** 主播端会话：持有与嘉宾的持久 TCP 连接（推送文件后保持打开，继续走控制命令） */
    private data class LeaderSession(
        val peer: LanPeer,
        var socket: Socket? = null,
        var out: OutputStream? = null,
    )

    /** 一起听控制命令（嘉宾侧解析后应用） */
    sealed interface LanControlCommand {
        /** 周期/事件状态同步：跳到 idx、对齐到 pos、按 playing 播放或暂停 */
        data class State(val idx: Int, val pos: Long, val playing: Boolean) : LanControlCommand

        /** 主播结束会话：嘉宾退出一起听 */
        object Stop : LanControlCommand
    }

    fun isLeaderSessionActive(): Boolean = leaderSession != null


    // ---------------- 生命周期 ----------------

    /** 应用启动时调用：开发现 + 开 TCP 服务 + 清上次异常退出的残留 */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        ctx = app
        myId = UUID.randomUUID().toString()
        myName = Build.MODEL?.takeIf { it.isNotBlank() } ?: "TENGMusic 设备"

        // 上次崩溃/被杀可能留下缓存歌曲与临时文件：启动即清，保证「退出不留残余」
        cleanupDir(File(app.cacheDir, LISTEN_DIR))
        cleanupDir(File(app.cacheDir, TMP_DIR))

        discovery = LanDiscovery(myId, myName).also { d ->
            d.start()
            scope.launch { d.peerList.collect { _peers.value = it } }
        }
        server = LanServer(::handleConnection).also { it.start(scope) }
        Log.i(TAG, "LanManager started: $myName ($myId)")
    }

    /** 应用退出时调用：发 bye + 关 socket */
    fun stop() {
        if (!started) return
        started = false
        discovery?.stop()
        server?.stop()
        discovery = null
        server = null
        _peers.value = emptyList()
    }

    /** 删除一起听缓存目录（退出一起听 / 启动清残留共用） */
    fun clearListenTogether() {
        ctx?.let { cleanupDir(File(it.cacheDir, LISTEN_DIR)) }
        _listenSongs.value = emptyList()
        leaderSession = null
    }

    private fun cleanupDir(dir: File) {
        runCatching { dir.deleteRecursively() }
    }

    // ---------------- 发送 ----------------

    /**
     * 传歌：把 [track] 推给 [peer]，对方接受后落其媒体库。
     * @param thresholdMb 压缩阈值（MB）：原文件超过它先压缩再传，对方收到后自动解压
     */
    fun sendSong(peer: LanPeer, track: Track, thresholdMb: Int) {
        val id = UUID.randomUUID().toString()
        addTransfer(
            TransferState(id, peer.name, track.displayName, LanProtocol.KIND_SONG, sending = true)
        )
        scope.launch {
            val header = JSONObject().apply {
                put("kind", LanProtocol.KIND_SONG)
                put("id", id)
                put("name", track.displayName)
                put("from", myName)
            }
            sendPayload(id, peer, track, header, thresholdMb) { done, total ->
                updateTransfer(id) { it.copy(done = done, total = total) }
            }
        }
    }

    /**
     * 一起听（主播侧）：把 [tracks] 整张临时歌单 + 各自歌词一次性邀请推给 [peer]。
     * 流程：发邀请头（含 count + 歌单元信息 + 各文件尺寸）→ 等一次接受 → 顺序推 N 个文件
     * → 连接保持打开，后续由 [sendListenState] 周期下发控制命令实现进度/切歌/暂停同步。
     * 文件只在嘉宾缓存目录，退出即删。
     */
    fun pushListenSession(
        peer: LanPeer,
        tracks: List<Track>,
        lyrics: List<String?>,
    ) {
        if (tracks.isEmpty()) return
        val id = UUID.randomUUID().toString()
        val app = ctx ?: return
        val resolver = app.contentResolver
        // 先量各文件尺寸（用于整体进度）
        val sizes = tracks.map { t ->
            runCatching { resolver.openAssetFileDescriptor(t.uri, "r")?.use { it.length } }
                .getOrNull() ?: -1L
        }
        val total = sizes.sumOf { if (it > 0) it else 0L }
        val queue = tracks.mapIndexed { i, t ->
            SharedMeta(t.title, t.artist, lyrics.getOrNull(i), 0L)
        }
        addTransfer(
            TransferState(id, peer.name, "一起听歌单(${tracks.size}首)", LanProtocol.KIND_LISTEN, sending = true, total = total)
        )
        scope.launch {
            val socket = try {
                Socket().apply {
                    connect(java.net.InetSocketAddress(peer.host, peer.port), 8000)
                    soTimeout = 15_000
                    tcpNoDelay = true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "listen connect failed", e)
                failTransfer(id, "连接失败：${e.message}", LanProtocol.KIND_LISTEN)
                return@launch
            }
            try {
                val out = socket.getOutputStream()
                val input = socket.getInputStream()
                // 1) 邀请头（UTF-8 JSON）
                val queueArr = JSONArray().apply {
                    queue.forEach { m ->
                        put(JSONObject().apply {
                            put("title", m.title)
                            put("artist", m.artist)
                            put("lyric", m.lyric ?: "")
                            put("dur", m.durMs)
                        })
                    }
                }
                val sizesArr = JSONArray().apply { sizes.forEach { put(it) } }
                val header = JSONObject().apply {
                    put("kind", LanProtocol.KIND_LISTEN)
                    put("id", id)
                    put("from", myName)
                    put("count", tracks.size)
                    put("queue", queueArr)
                    put("sizes", sizesArr)
                }
                LanTransfer.writeLine(out, header.toString())
                val reply = LanTransfer.readHeaderLine(input)
                if (reply != LanProtocol.REPLY_OK) {
                    finishTransfer(id, false, "对方拒绝了一起听")
                    return@launch
                }
                leaderSession = LeaderSession(peer, socket, out)
                // 2) 顺序推 N 个文件
                var cumulative = 0L
                tracks.forEachIndexed { i, t ->
                    val size = sizes[i]
                    LanTransfer.writeLine(out, size.toString())
                    val src = resolver.openInputStream(t.uri)
                    if (src == null || size <= 0) {
                        failTransfer(id, "读取歌曲失败：${t.title}", LanProtocol.KIND_LISTEN)
                        return@launch
                    }
                    val base = cumulative
                    val ok = LanTransfer.copyWithProgress(src, out, size) { done ->
                        updateTransfer(id) { it.copy(done = base + done, total = total) }
                    }
                    if (!ok) {
                        failTransfer(id, "传输中断", LanProtocol.KIND_LISTEN)
                        return@launch
                    }
                    cumulative += size
                }
                finishTransfer(id, true, "已建立一起听，正在同步…")
                // 3) 连接保持打开：控制命令由 sendListenState 继续写入
            } catch (e: Throwable) {
                Log.w(TAG, "pushListenSession error", e)
                failTransfer(id, e.message ?: "发送失败", LanProtocol.KIND_LISTEN)
                runCatching { socket.close() }
                leaderSession = null
            }
        }
    }

    /**
     * 发送主体：按需压缩 → 组头 → 连接发送 → 汇报进度 → 清理临时文件。
     * 压缩只在「原文件超过阈值」时发生（先落临时 zip 再发，头部因此带确定 size）。
     */
    private suspend fun sendPayload(
        id: String,
        peer: LanPeer,
        track: Track,
        header: JSONObject,
        thresholdMb: Int,
        onProgress: (Long, Long) -> Unit,
    ) {
        val app = ctx ?: return
        val resolver = app.contentResolver
        val kind = header.optString("kind", LanProtocol.KIND_SONG)
        var tmpZip: File? = null
        try {
            val rawSize = runCatching {
                resolver.openAssetFileDescriptor(track.uri, "r")?.use { it.length }
            }.getOrNull() ?: -1L
            val needZip = rawSize > 0 && rawSize > thresholdMb * 1024L * 1024L

            if (needZip) {
                val tmpDir = File(app.cacheDir, TMP_DIR).apply { mkdirs() }
                val zipFile = File(tmpDir, "send_$id.zip")
                val src = resolver.openInputStream(track.uri)
                if (src == null || !LanTransfer.zipToFile(src, track.displayName, zipFile)) {
                    failTransfer(id, "读取歌曲失败", kind)
                    return
                }
                tmpZip = zipFile
                header.put("size", zipFile.length())
                header.put("compressed", true)
            } else {
                header.put("size", rawSize)
                header.put("compressed", false)
            }

            val result = LanClient.send(
                peer = peer,
                headerJson = header.toString(),
                size = header.optLong("size", -1L),
                openInput = { tmpZip?.inputStream() ?: resolver.openInputStream(track.uri) },
                onProgress = { done -> onProgress(done, header.optLong("size", -1L)) },
            )
            result.fold(
                onSuccess = {
                    finishTransfer(id, true, "已发送给 ${peer.name}")
                },
                onFailure = { e ->
                    Log.w(TAG, "send failed", e)
                    failTransfer(id, e.message ?: "发送失败", kind)
                },
            )
        } catch (e: Throwable) {
            Log.w(TAG, "sendPayload error", e)
            failTransfer(id, e.message ?: "发送失败", kind)
        } finally {
            tmpZip?.delete()
        }
    }

    // ---------------- 接收（TCP 服务每连接） ----------------

    private suspend fun handleConnection(conn: Socket) {
        val app = ctx ?: return
        try {
            conn.soTimeout = 15_000
            conn.tcpNoDelay = true
            val input = conn.getInputStream()
            val out = conn.getOutputStream()

            val headerText = LanTransfer.readHeaderLine(input) ?: return
            val header = LanJson.parse(headerText) ?: run {
                LanTransfer.writeLine(out, LanProtocol.REPLY_NO)
                return
            }
            val kind = header.optString("kind")
            val id = header.optString("id").ifEmpty { UUID.randomUUID().toString() }
            val name = header.optString("name").ifEmpty { "未命名文件" }
            val size = header.optLong("size", -1L)
            val compressed = header.optBoolean("compressed", false)
            val from = header.optString("from").ifEmpty { "对端设备" }
            val peer = LanPeer(id = "remote", name = from, host = conn.inetAddress.hostAddress ?: "")

            when (kind) {
                LanProtocol.KIND_SONG ->
                    _pendingSongOffer.value = LanEvent.SongOffer(id, peer, name, size, compressed)
                LanProtocol.KIND_LISTEN -> {
                    // 一起听：整段会话（邀请 → 一次接受 → 收 N 文件 → 持续控制）在此处理
                    handleListenSession(conn, header, id, from)
                    return
                }
                else -> {
                    LanTransfer.writeLine(out, LanProtocol.REPLY_NO)
                    return
                }
            }

            // 等用户决定（90s 超时按拒绝处理）
            val deferred = CompletableDeferred<Boolean>()
            pending[id] = deferred
            val accepted = withTimeoutOrNull(90_000) { deferred.await() } ?: false
            pending.remove(id)
            if (!accepted) {
                LanTransfer.writeLine(out, LanProtocol.REPLY_NO)
                return
            }
            LanTransfer.writeLine(out, LanProtocol.REPLY_OK)

            conn.soTimeout = 60_000
            addTransfer(
                TransferState(id, from, name, kind, sending = false, total = if (size > 0) size else 0L)
            )
            val tmpDir = File(app.cacheDir, TMP_DIR).apply { mkdirs() }
            val tmp = File(tmpDir, "recv_$id.bin")
            var message = ""
            var ok = false
            try {
                ok = LanTransfer.copyWithProgress(
                    input, FileOutputStream(tmp), size
                ) { done ->
                    updateTransfer(id) { it.copy(done = done) }
                }
                if (!ok) throw IOException("负载不完整")
                if (kind == LanProtocol.KIND_SONG) {
                    val finalFile = if (compressed) {
                        LanTransfer.unzipFirstEntry(tmp, File(tmpDir, "unz_$id")) ?: throw IOException("解压失败")
                    } else tmp
                    val saved = saveToMediaStore(app, finalFile, name)
                    if (saved == null) throw IOException("写入媒体库失败")
                    message = saved
                } else {
                    // 一起听改走持久会话（handleListenSession），单文件通道不再承载
                    throw IOException("不支持的传输类型")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "receive failed", e)
                ok = false
                message = e.message ?: "接收失败"
            } finally {
                tmp.delete()
                cleanupDir(File(tmpDir, "unz_$id"))
            }
            updateTransfer(id) { it.copy(finished = true, ok = ok, message = message, done = it.total) }
            _pendingSongOffer.value = null
            _pendingListenOffer.value = null
            _events.tryEmit(
                LanEvent.Done(
                    transferId = id,
                    ok = ok,
                    message = message,
                    kind = kind,
                    sending = false,
                    title = header.optString("title"),
                    artist = header.optString("artist"),
                    lyric = header.optString("lyric").takeIf { it.isNotBlank() },
                )
            )
        } catch (e: Throwable) {
            Log.w(TAG, "handleConnection error", e)
        }
    }

    /** UI 对「待决定」请求作答（拒绝时立即收起询问卡） */
    fun respondOffer(transferId: String, accept: Boolean) {
        pending.remove(transferId)?.complete(accept)
        if (!accept) {
            if (_pendingSongOffer.value?.transferId == transferId) _pendingSongOffer.value = null
            if (_pendingListenOffer.value?.transferId == transferId) _pendingListenOffer.value = null
        }
    }

    /**
     * 一起听（嘉宾侧整段会话）：展示一次性邀请 → 等接受 → 顺序收 N 个文件（按序缓存成临时歌单）
     * → 接收成功后通知 UI 起播 → 连接保持打开，持续读取主播下发的控制命令（State/Stop）。
     */
    private suspend fun handleListenSession(
        conn: Socket,
        header: JSONObject,
        id: String,
        from: String,
    ) {
        val app = ctx ?: return
        try {
            conn.soTimeout = 15_000
            val out = conn.getOutputStream()
            val input = conn.getInputStream()
            val count = header.optInt("count", 1).coerceAtLeast(1)
            val queue = parseQueue(header.optJSONArray("queue"))
            val sizesArr = header.optJSONArray("sizes")
            val sizes = (0 until (sizesArr?.length() ?: 0)).map { sizesArr!!.optLong(it) }
            val peer = LanPeer(id = "remote", name = from, host = conn.inetAddress.hostAddress ?: "")
            _pendingListenOffer.value = LanEvent.ListenOffer(id, peer, count, queue)

            // 等用户一次决定（90s 超时按拒绝）
            val deferred = CompletableDeferred<Boolean>()
            pending[id] = deferred
            val accepted = withTimeoutOrNull(90_000) { deferred.await() } ?: false
            pending.remove(id)
            if (!accepted) {
                LanTransfer.writeLine(out, LanProtocol.REPLY_NO)
                _pendingListenOffer.value = null
                return
            }
            LanTransfer.writeLine(out, LanProtocol.REPLY_OK)

            // 顺序接收 N 个文件，按序缓存成临时歌单
            val tmpDir = File(app.cacheDir, TMP_DIR).apply { mkdirs() }
            val total = sizes.sumOf { if (it > 0) it else 0L }
            val songs = mutableListOf<SharedSong>()
            var ok = true
            var message = ""
            var cumulative = 0L
            try {
                conn.soTimeout = 60_000
                repeat(count) { i ->
                    val sizeLine = LanTransfer.readHeaderLine(input) ?: throw IOException("缺尺寸行")
                    val size = sizeLine.toLongOrNull() ?: throw IOException("尺寸非法")
                    val tmp = File(tmpDir, "recv_${id}_$i.bin")
                    val base = cumulative
                    val got = LanTransfer.copyWithProgress(input, FileOutputStream(tmp), size) { done ->
                        updateTransfer(id) { it.copy(done = base + done, total = total) }
                    }
                    if (!got) throw IOException("文件不完整")
                    val meta = queue.getOrNull(i) ?: SharedMeta("曲目${i + 1}", "", null, 0L)
                    val staged = stageListenFile(app, "${id}_$i", tmp) ?: throw IOException("缓存失败")
                    songs += SharedSong(meta, staged)
                    cumulative += size
                }
                _listenSongs.value = songs
                message = "已接收 $count 首，开始一起听"
                updateTransfer(id) { it.copy(finished = true, ok = true, message = message, done = total) }
            } catch (e: Throwable) {
                Log.w(TAG, "listen recv failed", e)
                ok = false
                message = e.message ?: "接收失败"
                updateTransfer(id) { it.copy(finished = true, ok = false, message = message) }
            }
            _pendingListenOffer.value = null
            _events.tryEmit(
                LanEvent.Done(id, ok, message, kind = LanProtocol.KIND_LISTEN, sending = false)
            )

            // 连接保持打开：持续读取主播下发的控制命令直到断开
            if (ok) {
                try {
                    while (true) {
                        val line = LanTransfer.readHeaderLine(input, 1 shl 16) ?: break
                        val cmd = LanJson.parse(line) ?: break
                        when (cmd.optString("t")) {
                            LanProtocol.CTRL_STOP -> {
                                _controlCommands.tryEmit(LanControlCommand.Stop)
                                break
                            }
                            LanProtocol.CTRL_STATE -> {
                                _controlCommands.tryEmit(
                                    LanControlCommand.State(
                                        idx = cmd.optInt("idx", 0),
                                        pos = cmd.optLong("pos", 0L),
                                        playing = cmd.optBoolean("playing", true),
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Throwable) { /* 连接断开 */ }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "handleListenSession error", e)
        }
    }

    /** 解析歌单 JSON 数组为 [SharedMeta] 列表 */
    private fun parseQueue(arr: org.json.JSONArray?): List<SharedMeta> {
        if (arr == null) return emptyList()
        val list = mutableListOf<SharedMeta>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list += SharedMeta(
                title = o.optString("title").ifEmpty { "曲目${i + 1}" },
                artist = o.optString("artist"),
                lyric = o.optString("lyric").takeIf { it.isNotBlank() },
                durMs = o.optLong("dur", 0L),
            )
        }
        return list
    }

    /** 一起听文件落缓存目录（按 key 命名，退出一起听整体删除） */
    private fun stageListenFile(app: Context, key: String, tmp: File): File? {
        val dir = File(app.cacheDir, LISTEN_DIR).apply { mkdirs() }
        val dest = File(dir, "$key.mp3")
        if (!tmp.renameTo(dest)) {
            tmp.inputStream().use { i ->
                FileOutputStream(dest).use { o -> i.copyTo(o, 64 * 1024) }
            }
            tmp.delete()
        }
        return dest
    }

    /** 主播下发一次状态同步（嘉宾据此对齐 idx/pos/playing） */
    fun sendListenState(idx: Int, pos: Long, playing: Boolean) {
        val s = leaderSession ?: return
        try {
            s.out?.let {
                LanTransfer.writeLine(
                    it,
                    JSONObject().apply {
                        put("t", LanProtocol.CTRL_STATE)
                        put("idx", idx)
                        put("pos", pos)
                        put("playing", playing)
                    }.toString()
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "sendListenState failed", e)
        }
    }

    /** 主播结束一起听：下发 Stop 并关闭控制连接 */
    fun endListenSession() {
        val s = leaderSession ?: return
        try {
            s.out?.let {
                LanTransfer.writeLine(it, JSONObject().put("t", LanProtocol.CTRL_STOP).toString())
            }
        } catch (_: Throwable) {
        }
        runCatching { s.socket?.close() }
        leaderSession = null
    }

    /** 嘉宾端取已落盘、按序排列的临时歌单 */
    fun getListenSongs(): List<SharedSong> = _listenSongs.value

    // ---------------- 落盘（传歌） ----------------

    /** 保存到公共音乐目录：API 29+ 走 MediaStore RELATIVE_PATH；以下直接插行写文件 */
    private fun saveToMediaStore(app: Context, src: File, displayName: String): String? {
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeFromName(displayName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/TENGMUSIC")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri: Uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out, 64 * 1024) }
            } ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, done, null, null)
            }
            src.delete()
            "已保存到 音乐/TENGMUSIC/$displayName"
        } catch (e: Throwable) {
            Log.w(TAG, "saveToMediaStore failed", e)
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a", "mp4", "aac" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/x-wav"
        else -> "audio/*"
    }

    // ---------------- 传输状态 ----------------

    private fun addTransfer(state: TransferState) {
        _transfers.value = _transfers.value + state
    }

    private fun updateTransfer(id: String, transform: (TransferState) -> TransferState) {
        _transfers.value = _transfers.value.map { if (it.id == id) transform(it) else it }
    }

    private fun finishTransfer(id: String, ok: Boolean, message: String) {
        updateTransfer(id) {
            it.copy(finished = true, ok = ok, message = message, done = it.total)
        }
    }

    private fun failTransfer(id: String, message: String, kind: String = LanProtocol.KIND_SONG) {
        finishTransfer(id, false, message)
        _events.tryEmit(LanEvent.Done(id, false, message, kind = kind, sending = true))
    }

    /** 清掉已结束的传输记录（UI 点了「知道了」后调用） */
    fun dismissTransfer(id: String) {
        _transfers.value = _transfers.value.filterNot { it.id == id }
    }
}
