package com.tengwear.tengmusic

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.Coil
import coil.request.ImageRequest
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 播放器状态中枢：
 * - 扫描本地音乐、按专辑分组
 * - 连接媒体会话服务（MediaController）
 * - 向 UI 暴露曲目/播放状态/进度/歌词
 * - 音效（EQ）、播放模式、速度、睡眠定时、收藏
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()

    /** 按专辑分组后的列表（iPod 风格「专辑选曲」数据源） */
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums = _albums.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex = _currentIndex.asStateFlow()

    /**
     * 当前播放曲目（归类「双击歌名 → 加入类别」时用）。
     * 必须是 StateFlow：早期版本是普通 getter，属于一次性快照——
     * ViewModel 重建后 _currentIndex 仍为 -1，快照永远是 null，
     * 导致归类模式下点任何类别都静默无反馈（"添加不了"）。
     * 现由 poll() 随 currentMediaItemIndex 同步，保持响应式。
     */
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume = _volume.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val lyrics = _lyrics.asStateFlow()

    private val _lyricIndex = MutableStateFlow(-1)
    val lyricIndex = _lyricIndex.asStateFlow()

    // ---------------- 音效 EQ ----------------

    private val _eqEnabled = MutableStateFlow(true)
    val eqEnabled = _eqEnabled.asStateFlow()

    /** 各频段中心频率（Hz）；为空表示均衡器尚未就绪 */
    private val _eqFreqs = MutableStateFlow<List<Int>>(emptyList())
    val eqFreqs = _eqFreqs.asStateFlow()

    /** 各频段当前增益（millibel），与 eqFreqs 一一对应 */
    private val _eqGains = MutableStateFlow<List<Int>>(emptyList())
    val eqGains = _eqGains.asStateFlow()

    private val _eqPreset = MutableStateFlow(EqPresets.ALL.first().name)
    val eqPreset = _eqPreset.asStateFlow()

    /** 设备内置预设名称列表（来自系统 Equalizer，移植自 AndroidEqualizer） */
    private val _eqDevicePresets = MutableStateFlow<List<String>>(emptyList())
    val eqDevicePresets = _eqDevicePresets.asStateFlow()

    // ---------------- 播放模式 / 速度 / 定时 ----------------

    /** Player.REPEAT_MODE_OFF / ONE / ALL */
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle = _shuffle.asStateFlow()

    /** 播放器左下角的三态播放模式（单曲循环 / 列表随机 / 列表顺序） */
    private val _playMode = MutableStateFlow(PlayMode.SEQUENTIAL)
    val playMode = _playMode.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed = _speed.asStateFlow()

    /** 睡眠定时剩余分钟数，0 表示未开启 */
    private val _sleepMinutes = MutableStateFlow(0)
    val sleepMinutes = _sleepMinutes.asStateFlow()

    /** 收藏的曲目 id 集合 */
    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites = _favorites.asStateFlow()

    // ---------------- 播放归类（智能列表） ----------------

    /** 归类列表（内置：全部歌曲 / 收藏；其余为用户自定义） */
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.asStateFlow()

    /** 当前播放队列是否为「全部曲目」：专辑流据此决定初始居中定位 */
    private val _queueIsAll = MutableStateFlow(true)
    val queueIsAll = _queueIsAll.asStateFlow()

    /**
     * 当前播放队列的曲目 id 序列（null = 全部曲目顺序）。
     * 播放归类时队列是类别子集，controller 的 currentMediaItemIndex 是**队列内下标**，
     * 而 _currentIndex 的语义是**全局曲目下标**（标题/歌词/专辑色/专辑流定位都依赖它）。
     * 旧代码直接把队列内下标当全局下标用，播完归类后标题显示错歌、currentTrack 错乱，
     * 「添加到归类」加错歌/加不上正是由此引发。poll 时经此映射回全局下标。
     */
    private var activeQueueIds: List<Long>? = null

    // ---------------- 播放器外观设置（主题 / 进度环） ----------------

    /** 播放器 UI 主题：0 = 经典（白色半透明胶囊），1 = M3E（底部三瓣长胶囊） */
    private val _uiTheme = MutableStateFlow(SettingsRepository.getUiTheme(getApplication()))
    val uiTheme = _uiTheme.asStateFlow()

    /** 进度环半径占屏幕短边比例（0.30 ~ 0.47） */
    private val _ringRadiusFrac = MutableStateFlow(SettingsRepository.getRingRadiusFrac(getApplication()))
    val ringRadiusFrac = _ringRadiusFrac.asStateFlow()

    /** 进度环描边粗细（dp） */
    private val _ringStroke = MutableStateFlow(SettingsRepository.getRingStroke(getApplication()))
    val ringStroke = _ringStroke.asStateFlow()

    /**
     * 进度条样式：0 = 圆环（默认），1 = 波浪圆环（M3E 官方
     * CircularWavyProgressIndicator 同款：轨道平弧 + 进度正弦波沿弧流动）。
     * 两种样式互斥，设置页可切换。
     */
    private val _progressStyle =
        MutableStateFlow(SettingsRepository.getProgressStyle(getApplication()))
    val progressStyle = _progressStyle.asStateFlow()

    // ---------------- 背景观感（播放页专辑封面背景） ----------------

    /** 背景模糊半径（dp），0 = 不模糊 */
    private val _blurRadius = MutableStateFlow(16f)
    val blurRadius = _blurRadius.asStateFlow()

    /** 背景暗化强度（0 = 清晰明亮，1 = 全黑），用于压暗专辑封面背景以保证文字可读 */
    private val _dimAlpha = MutableStateFlow(0.55f)
    val dimAlpha = _dimAlpha.asStateFlow()

    // ---------------- 歌词颜色 ----------------

    /** 当前曲目专辑封面的主色（0xAARRGGBB），无封面/失败时为 null */
    private val _albumColor = MutableStateFlow<Int?>(null)
    val albumColor = _albumColor.asStateFlow()

    /** 歌词颜色模式：0 = 跟随专辑主色，1 = 用户自定义 */
    private val _lyricColorMode = MutableStateFlow(
        SettingsRepository.getLyricColorMode(getApplication())
    )
    val lyricColorMode = _lyricColorMode.asStateFlow()

    /** 用户自定义歌词颜色（0xAARRGGBB），默认白色 */
    private val _lyricCustomColor = MutableStateFlow(
        SettingsRepository.getLyricCustomColor(getApplication())
    )
    val lyricCustomColor = _lyricCustomColor.asStateFlow()

    /** 解析后的歌词颜色（最终着色用）：跟随专辑时取 albumColor，否则取自定义色 */
    private val _lyricColor = MutableStateFlow(0xFFFFFFFF.toInt())
    val lyricColor = _lyricColor.asStateFlow()

    // ---------------- 歌词切换动画 ----------------

    /** 用户选择的歌词切换动画（设置里可选，带实时预览） */
    private val _lyricTransition = MutableStateFlow(
        com.tengwear.tengmusic.ui.LyricTransition.fromId(
            SettingsRepository.getLyricTransition(getApplication())
        )
    )
    val lyricTransition = _lyricTransition.asStateFlow()

    // ---------------- 歌词字号 ----------------

    /** 歌词字号（sp，12 ~ 22），设置里可调，歌词页实时生效 */
    private val _lyricFontSize = MutableStateFlow(
        SettingsRepository.getLyricFontSize(getApplication())
    )
    val lyricFontSize = _lyricFontSize.asStateFlow()

    // ---------------- 封面工具 ----------------

    /** 封面工具最近一次操作结果（写入中 / 成功 / 失败），供界面提示 */
    private val _coverMessage = MutableStateFlow<String?>(null)
    val coverMessage = _coverMessage.asStateFlow()

    private var controller: MediaController? = null
    private var sleepJob: Job? = null
    /**
     * 播放意图缓存：MediaController 异步连接（connectService 在曲目扫描之后才发起，
     * future 还要再等一拍），连接完成前用户点归类/选曲时 controller 为 null，
     * 旧代码直接静默 no-op（表现为"点击归类没有自动播放"）。
     * 连接成功后由 connectService 的 listener 统一补播。
     */
    private var pendingPlay: (() -> Unit)? = null

    init {
        // 预置默认 5 段均衡器（低频→高频），保证「音效」页未播放时也能看到并手动调节，
        // 而不是只剩一个开/关开关。真实频段会在首次播放 attach 后用设备值覆盖。
        _eqFreqs.value = listOf(60, 230, 910, 3600, 14000)
        _eqGains.value = List(5) { 0 }
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = AudioRepository.loadTracks(getApplication())
            _tracks.value = loaded
            _albums.value = groupTracksIntoAlbums(loaded)
            _favorites.value = FavoritesRepository.load(getApplication())
            _categories.value = CategoryRepository.load(getApplication())
            connectService()
        }
        viewModelScope.launch { poll() }
        recomputeLyricColor()
    }

    private fun connectService() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, PlaybackService::class.java))
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            controller = future.get()
            val items = _tracks.value.map { MediaItem.fromUri(it.uri) }
            controller?.setMediaItems(items)
            controller?.prepare()
            // 补播连接前缓存的播放意图（点击归类/选曲时控制器尚未就绪）
            val pending = pendingPlay
            if (pending != null) {
                pendingPlay = null
                pending()
            }
        }, MoreExecutors.directExecutor())
    }

    /** 轮询播放进度与歌词高亮（框架用，足够流畅） */
    private suspend fun poll() {
        while (true) {
            delay(200)
            val c = controller ?: continue
            _position.value = c.currentPosition.coerceAtLeast(0L)
            _duration.value = if (c.duration > 0) c.duration else 0L
            _isPlaying.value = c.isPlaying
            _volume.value = c.volume.coerceIn(0f, 1f)
            _repeatMode.value = c.repeatMode
            _shuffle.value = c.shuffleModeEnabled
            // 反推三态模式：这样「设置页」单独改 repeat/shuffle 时，播放器按钮也能同步
            _playMode.value = when {
                c.repeatMode == Player.REPEAT_MODE_ONE -> PlayMode.REPEAT_ONE
                c.shuffleModeEnabled -> PlayMode.SHUFFLE
                else -> PlayMode.SEQUENTIAL
            }
            _speed.value = c.playbackParameters.speed
            val idx = resolveGlobalIndex(c.currentMediaItemIndex)
            if (idx != _currentIndex.value) {
                _currentIndex.value = idx
                _currentTrack.value = _tracks.value.getOrNull(idx)
                loadLyricsFor(idx)
                updateAlbumColor(idx)
            }
            syncEq()
            updateLyric(_position.value)
        }
    }

    /** 均衡器就绪后同步频段与默认增益（保留用户已调的增益，避免每次轮询都被重置） */
    private fun syncEq() {
        val count = AudioEffects.bandCount
        if (count > 0) {
            val prevGains = _eqGains.value
            if (_eqFreqs.value.size != count) {
                _eqFreqs.value = AudioEffects.centerFreqsHz
                // 设备内置预设（移植自 AndroidEqualizer 的 EqualizerManager.usePreset）
                _eqDevicePresets.value = AudioEffects.devicePresetNames
                // 频段数一致则沿用用户增益；否则退回预设默认
                val gains = if (prevGains.size == count) {
                    prevGains
                } else {
                    List(count) { i -> EqPresets.ALL.first().gains.getOrElse(i) { 0 } }
                }
                _eqGains.value = gains
                gains.forEachIndexed { i, g -> AudioEffects.setBandLevel(i, g) }
            }
            AudioEffects.setEnabled(_eqEnabled.value)
        }
    }

    /**
     * 把 controller 的队列内下标映射为全局曲目下标。
     * activeQueueIds 为 null（全部曲目队列）时两者一致；
     * 播放归类时经 id 反查全局下标，查不到则原样返回（容错）。
     */
    private fun resolveGlobalIndex(queueIdx: Int): Int {
        val ids = activeQueueIds ?: return queueIdx
        val id = ids.getOrNull(queueIdx) ?: return queueIdx
        return _tracks.value.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: queueIdx
    }

    private fun loadLyricsFor(idx: Int) {
        val t = _tracks.value.getOrNull(idx) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _lyrics.value = LyricsRepository.loadLyrics(getApplication(), t)
            _lyricIndex.value = -1
        }
    }

    /** 切换曲目时异步计算专辑封面主色，并据当前模式刷新歌词颜色 */
    private fun updateAlbumColor(idx: Int) {
        val t = _tracks.value.getOrNull(idx) ?: run {
            _albumColor.value = null
            recomputeLyricColor()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val color = computeAlbumColor(t.albumArtUri)
            _albumColor.value = color
            recomputeLyricColor()
        }
    }

    /** 取封面主色：缩到 32×32 求平均（跳纯黑/纯白边），再提升饱和与亮度得到悦目歌词色 */
    private suspend fun computeAlbumColor(uri: Uri): Int? = withContext(Dispatchers.IO) {
        try {
            val req = ImageRequest.Builder(getApplication())
                .data(uri)
                .size(32, 32)
                .allowHardware(false)
                .build()
            val drawable = Coil.imageLoader(getApplication()).execute(req).drawable ?: return@withContext null
            val bmp = drawable.toBitmap(32, 32)
            val px = IntArray(32 * 32)
            bmp.getPixels(px, 0, 32, 0, 0, 32, 32)
            var r = 0; var g = 0; var b = 0; var n = 0
            for (c in px) {
                if ((c shr 24 and 0xFF) < 128) continue
                r += c shr 16 and 0xFF
                g += c shr 8 and 0xFF
                b += c and 0xFF
                n++
            }
            if (n == 0) return@withContext null
            vividFromRgb(r / n, g / n, b / n)
        } catch (_: Throwable) {
            null
        }
    }

    /** RGB → 提升饱和(≥0.55)/亮度(≈0.6)后的悦目色；纯灰也转成明确色相避免发灰 */
    private fun vividFromRgb(r: Int, g: Int, b: Int): Int {
        val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
        val max = max(rf, max(gf, bf)); val min = min(rf, min(gf, bf))
        val d = max - min
        var h = 0f; var s = 0f
        if (d > 1e-4f) {
            s = if (max + min > 1f) d / (2f - max - min) else d / (max + min)
            h = when (max) {
                rf -> (gf - bf) / d + if (gf < bf) 6f else 0f
                gf -> (bf - rf) / d + 2f
                else -> (rf - gf) / d + 4f
            } / 6f
        }
        return hslToRgb(h, max(s, 0.55f), 0.60f)
    }

    private fun hslToRgb(h: Float, s: Float, l: Float): Int {
        val r: Float; val g: Float; val b: Float
        if (s <= 0f) {
            r = l; g = l; b = l
        } else {
            val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
            val p = 2 * l - q
            r = hue2rgb(p, q, h + 1f / 3f)
            g = hue2rgb(p, q, h)
            b = hue2rgb(p, q, h - 1f / 3f)
        }
        val ri = (r * 255).toInt().coerceIn(0, 255)
        val gi = (g * 255).toInt().coerceIn(0, 255)
        val bi = (b * 255).toInt().coerceIn(0, 255)
        return 0xFF000000.toInt() or (ri shl 16) or (gi shl 8) or bi
    }

    private fun hue2rgb(p: Float, q: Float, t: Float): Float {
        var tt = t
        if (tt < 0f) tt += 1f
        if (tt > 1f) tt -= 1f
        return when {
            tt < 1f / 6f -> p + (q - p) * 6f * tt
            tt < 1f / 2f -> q
            tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
            else -> p
        }
    }

    // ---------------- 歌词颜色对外 API ----------------

    /** 歌词颜色模式：0=跟随专辑，1=自定义 */
    fun setLyricColorMode(mode: Int) {
        _lyricColorMode.value = mode
        SettingsRepository.setLyricColorMode(getApplication(), mode)
        recomputeLyricColor()
    }

    /** 设定自定义歌词颜色（同时把模式切到自定义） */
    fun setLyricCustomColor(color: Int) {
        _lyricCustomColor.value = color
        SettingsRepository.setLyricCustomColor(getApplication(), color)
        _lyricColorMode.value = 1
        SettingsRepository.setLyricColorMode(getApplication(), 1)
        recomputeLyricColor()
    }

    /** 根据当前模式解析最终歌词颜色 */
    private fun recomputeLyricColor() {
        _lyricColor.value = if (_lyricColorMode.value == 0) {
            _albumColor.value ?: 0xFFFFFFFF.toInt()
        } else {
            _lyricCustomColor.value
        }
    }

    // ---------------- 歌词切换动画 API ----------------

    fun setLyricTransition(anim: com.tengwear.tengmusic.ui.LyricTransition) {
        _lyricTransition.value = anim
        SettingsRepository.setLyricTransition(getApplication(), anim.ordinal)
    }

    fun setLyricFontSize(sp: Float) {
        _lyricFontSize.value = sp.coerceIn(12f, 22f)
        SettingsRepository.setLyricFontSize(getApplication(), sp)
    }

    // ---------------- 封面工具 API ----------------

    /**
     * 给音频文件写入专辑封面（ID3v2.3 APIC 帧）。
     * 通过 SAF 读原文件全量字节 → 重建标签 → 覆盖写回，保留标题/艺术家/专辑帧。
     */
    fun writeTrackCoverArt(track: Track, imageBytes: ByteArray, mime: String) {
        _coverMessage.value = "正在写入…"
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = Id3Tag.writeCoverArt(
                getApplication(), track.uri, imageBytes, mime,
                title = track.title, artist = track.artist, album = track.album,
            )
            _coverMessage.value = if (ok) "已写入封面：${track.title}" else "写入失败：$msg"
        }
    }

    fun clearCoverMessage() {
        _coverMessage.value = null
    }

    /**
     * 批量写入封面（封面工具）：按用户确认后的匹配对依次写入，
     * 逐个汇报进度（"3/8 已写入：xxx"），失败项继续处理不中断。
     */
    fun writeCoverArtBatch(pairs: List<Pair<Track, ByteArray>>) {
        if (pairs.isEmpty()) {
            _coverMessage.value = "没有待写入的封面"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            var ok = 0
            var fail = 0
            pairs.forEachIndexed { i, (track, bytes) ->
                _coverMessage.value = "正在写入 ${i + 1}/${pairs.size}：${track.title}"
                val (success, _) = Id3Tag.writeCoverArt(
                    getApplication(), track.uri, bytes, "image/jpeg",
                    title = track.title, artist = track.artist, album = track.album,
                )
                if (success) ok++ else fail++
            }
            _coverMessage.value = if (fail == 0) {
                "批量写入完成：$ok 个封面已更新"
            } else {
                "批量写入完成：成功 $ok，失败 $fail"
            }
        }
    }

    private fun updateLyric(posMs: Long) {
        val list = _lyrics.value
        if (list.isEmpty()) {
            if (_lyricIndex.value != -1) _lyricIndex.value = -1
            return
        }
        var i = -1
        for (k in list.indices) {
            if (list[k].timeMs <= posMs) i = k else break
        }
        if (i != _lyricIndex.value) _lyricIndex.value = i
    }

    // ---------------- 对外控制 API ----------------

    /**
     * 选曲播放（专辑流 / 列表点击）：始终以「全部曲目」重建队列并定位到该曲，
     * 保证索引与全局曲目列表一致（从收藏 / 自定义归类切回专辑流时也能正确对齐）。
     * controller 未就绪时缓存意图，连接后自动补播（不再静默丢失）。
     */
    fun selectTrack(i: Int) {
        val items = _tracks.value.map { MediaItem.fromUri(it.uri) }
        if (items.isEmpty()) return
        val idx = i.coerceIn(0, items.lastIndex)
        val c = controller
        if (c == null) {
            pendingPlay = {
                controller?.let { cc ->
                    cc.setMediaItems(items)
                    cc.seekToDefaultPosition(idx)
                    cc.play()
                }
            }
            return
        }
        c.setMediaItems(items)
        c.seekToDefaultPosition(idx)
        c.play()
        _queueIsAll.value = true
        activeQueueIds = null // 全部曲目队列：队列下标即全局下标
    }

    fun togglePlay() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() = controller?.seekToNextMediaItem()

    fun prev() {
        controller?.let {
            if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem()
        }
    }

    fun seekToFraction(f: Float) {
        val d = _duration.value
        if (d > 0) controller?.seekTo((d * f).toLong())
    }

    /**
     * 跳到指定歌词时间戳并继续播放（点击歌词行从该句续播）。
     * 若当前处于暂停状态则自动恢复播放。
     */
    fun seekToLyric(timeMs: Long) {
        controller?.let {
            it.seekTo(timeMs.coerceAtLeast(0L))
            if (!it.isPlaying) it.play()
        }
    }

    /**
     * 相对当前位置 seek（前进/后退），供长按上一首/下一首调进度用。
     * 自动钳制到 [0, duration]，避免溢出。
     */
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val d = if (c.duration > 0) c.duration else _duration.value
        val target = (c.currentPosition + deltaMs).coerceIn(0L, d.coerceAtLeast(0L))
        c.seekTo(target)
    }

    fun setVolume(v: Float) {
        controller?.volume = v.coerceIn(0f, 1f)
        _volume.value = v.coerceIn(0f, 1f)
    }

    /** 播放某张专辑：默认从第一首开始，可指定专辑内第几首 */
    fun playAlbum(album: Album, indexInAlbum: Int = 0) {
        val globalIndex = album.trackIndices.getOrNull(indexInAlbum) ?: return
        selectTrack(globalIndex)
    }

    // ---------------- 归类（智能列表） ----------------

    /** 新增自定义类别（系统键盘命名后调用） */
    fun addCategory(name: String) {
        _categories.value = CategoryRepository.addCategory(getApplication(), name)
    }

    /** 拖拽重排：传入新的 id 顺序 */
    fun reorderCategories(orderedIds: List<String>) {
        _categories.value = CategoryRepository.reorder(getApplication(), orderedIds)
    }

    /** 删除自定义类别（内置类别忽略） */
    fun removeCategory(id: String) {
        _categories.value = CategoryRepository.removeCategory(getApplication(), id)
    }

    /**
     * 归类模式（双击歌名唤起列表时）：把当前曲目加入指定自定义类别。
     * 内置「全部歌曲」不接受单曲加入；「收藏」走收藏开关。
     * @return 是否成功加入
     */
    fun addTrackToCategory(trackId: Long, categoryId: String): Boolean {
        val list = CategoryRepository.addTrack(getApplication(), trackId, categoryId)
        _categories.value = list
        return list.any { it.id == categoryId && it.trackIds.contains(trackId) }
    }

    /** 归类模式：把当前曲目从指定自定义类别移出 */
    fun removeTrackFromCategory(trackId: Long, categoryId: String) {
        _categories.value = CategoryRepository.removeTrack(getApplication(), trackId, categoryId)
    }

    /**
     * 播放某个归类：按类别类型解析曲目子集，重建播放队列并从头播放。
     * - 全部歌曲：全部曲目
     * - 收藏：收藏集合
     * - 自定义（无歌曲时降级为全部）
     * @return false = 类别不存在或该归类解析不出曲目（调用方应提示且不关页）
     */
    fun playCategory(id: String): Boolean {
        val cat = _categories.value.find { it.id == id } ?: return false
        val items = cat.resolveTracks(_tracks.value, _favorites.value)
        if (items.isEmpty()) return false
        playItems(items)
        _queueIsAll.value = (cat.type == CategoryRepository.TYPE_ALL)
        return true
    }

    /** 用给定曲目列表重建播放队列并播放（从头）。controller 未就绪时缓存，连接后补播 */
    private fun playItems(items: List<Track>) {
        if (items.isEmpty()) return
        // 记录队列 id 序列：poll 据此把队列内下标映射回全局下标（见 resolveGlobalIndex）
        activeQueueIds = items.map { it.id }
        val c = controller
        if (c == null) {
            pendingPlay = {
                controller?.let { cc ->
                    cc.setMediaItems(items.map { MediaItem.fromUri(it.uri) })
                    cc.seekToDefaultPosition(0)
                    cc.play()
                }
            }
            return
        }
        c.setMediaItems(items.map { MediaItem.fromUri(it.uri) })
        c.seekToDefaultPosition(0)
        c.play()
    }

    // ---------------- 播放器外观设置 ----------------

    fun setUiTheme(theme: Int) {
        _uiTheme.value = theme
        SettingsRepository.setUiTheme(getApplication(), theme)
    }

    fun setRingRadiusFrac(frac: Float) {
        // 注意：此钳制必须与设置页滑块量程、SettingsRepository 保持一致。
        // 上限 0.50 是物理极限：圆环直径 = 屏幕短边 × frac × 2，frac=0.50 时圆环
        // 正好顶到圆屏边缘；超过 0.50 圆环会飞出屏幕被裁掉（不可见）。
        // 下限 0.05：圆环可缩到很小（直径 ≈ 屏幕短边 × 0.1）。
        _ringRadiusFrac.value = frac.coerceIn(0.05f, 0.50f)
        SettingsRepository.setRingRadiusFrac(getApplication(), frac)
    }

    fun setRingStroke(dp: Float) {
        _ringStroke.value = dp.coerceIn(2f, 12f)
        SettingsRepository.setRingStroke(getApplication(), dp)
    }

    /** 切换进度条样式（0 = 圆环，1 = 波浪） */
    fun setProgressStyle(style: Int) {
        val v = if (style == SettingsRepository.PROGRESS_STYLE_WAVY) {
            SettingsRepository.PROGRESS_STYLE_WAVY
        } else {
            SettingsRepository.PROGRESS_STYLE_RING
        }
        _progressStyle.value = v
        SettingsRepository.setProgressStyle(getApplication(), v)
    }

    // ---------------- EQ ----------------

    fun setEqEnabled(on: Boolean) {
        _eqEnabled.value = on
        AudioEffects.setEnabled(on)
    }

    /** 调整单个频段增益（millibel），增益范围见 AudioEffects */
    fun setBandGain(band: Int, millibel: Int) {
        val list = _eqGains.value.toMutableList()
        if (band !in list.indices) return
        val range = AudioEffects.minLevel..AudioEffects.maxLevel
        val v = millibel.coerceIn(range)
        list[band] = v
        _eqGains.value = list
        AudioEffects.setBandLevel(band, v)
        _eqPreset.value = "自定义"
    }

    /** 应用预设；设备频段数与预设长度不一致时自动截断/补零 */
    fun applyPreset(preset: EqPreset) {
        val count = _eqFreqs.value.size
        if (count == 0) return
        val gains = List(count) { i -> preset.gains.getOrElse(i) { 0 } }
        _eqGains.value = gains
        _eqPreset.value = preset.name
        gains.forEachIndexed { i, g -> AudioEffects.setBandLevel(i, g) }
    }

    /**
     * 应用设备内置预设（系统 Equalizer 自带预设，移植自 AndroidEqualizer）。
     * 应用后把各频段真实增益读回 UI 状态。
     */
    fun applyDevicePreset(index: Int) {
        AudioEffects.useDevicePreset(index)
        val count = _eqFreqs.value.size
        if (count == 0) return
        val gains = (0 until count).map { AudioEffects.manager().getBandLevel(it) }
        _eqGains.value = gains
        _eqPreset.value = AudioEffects.devicePresetNames.getOrNull(index) ?: "自定义"
    }

    // ---------------- 播放模式 / 速度 / 定时 ----------------

    /** 顺序 → 列表循环 → 单曲循环 → 顺序 */
    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = next
        controller?.repeatMode = next
    }

    fun toggleShuffle() {
        val next = !_shuffle.value
        _shuffle.value = next
        controller?.shuffleModeEnabled = next
    }

    /** 设置三态播放模式（同步到底层 repeatMode + shuffle） */
    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
        val (repeat, shuffleOn) = when (mode) {
            PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE to false
            PlayMode.SHUFFLE -> Player.REPEAT_MODE_ALL to true
            PlayMode.SEQUENTIAL -> Player.REPEAT_MODE_ALL to false
        }
        _repeatMode.value = repeat
        _shuffle.value = shuffleOn
        controller?.let {
            it.repeatMode = repeat
            it.shuffleModeEnabled = shuffleOn
        }
    }

    /** 单曲循环 → 列表随机 → 列表顺序 → 单曲循环 */
    fun cyclePlayMode() = setPlayMode(_playMode.value.next())

    fun setSpeed(v: Float) {
        val s = v.coerceIn(0.5f, 2.0f)
        _speed.value = s
        controller?.setPlaybackSpeed(s)
    }

    /** 睡眠定时：minutes<=0 取消；到点自动暂停 */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        _sleepMinutes.value = minutes
        if (minutes <= 0) return
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _sleepMinutes.value = 0
        }
    }

    /** 切换收藏 */
    fun toggleFavorite(trackId: Long) {
        _favorites.value = FavoritesRepository.toggle(getApplication(), trackId)
    }

    // ---------------- 背景观感 ----------------

    fun setBlurRadius(v: Float) {
        _blurRadius.value = v.coerceIn(0f, 40f)
    }

    /** brightness: 0(暗) ~ 100(亮)；亮度越高，暗化越弱 */
    fun setBackgroundBrightness(brightness: Float) {
        val b = brightness.coerceIn(0f, 100f)
        _dimAlpha.value = ((100f - b) / 100f * 0.95f).coerceIn(0f, 0.95f)
    }
}
