package com.tengwear.tengmusic.lan

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * TCP 传输原语（端口 [LanProtocol.TCP_PORT]）。
 *
 * 帧格式：一行 UTF-8 JSON 头（'\n' 结尾）+ 原始负载字节。
 *  - 头发送方写完必须 flush，并等待接收方一行 "OK"/"NO" 应答再发负载；
 *  - 负载长度以头里的 size 为准（接收方读满 size 即止，不会多吃）。
 *
 * 这里只放与业务无关的字节搬运 / 压缩解压；接收方的落盘决策在 LanManager。
 */
internal object LanTransfer {

    private const val TAG = "LanTransfer"
    private const val BUF = 64 * 1024

    /**
     * 从流里读一行（遇 '\n' 止，不缓冲超额字节，保证后续还能原样读二进制）。
     * 以 UTF-8 解码：先前逐字节 [Byte.toChar] 会把中文等多字节字符拆成乱码，
     * 导致标题/艺术家/歌词中文全部乱码（#110）。
     */
    fun readHeaderLine(input: InputStream, maxBytes: Int = 1 shl 20): String? {
        val buf = ByteArrayOutputStream()
        while (buf.size() < maxBytes) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString(Charsets.UTF_8)
            if (b == '\n'.code) return buf.toString(Charsets.UTF_8)
            buf.write(b)
        }
        return null
    }

    /** 写一行（自动补 '\n' 并 flush） */
    fun writeLine(out: OutputStream, text: String) {
        out.write(text.toByteArray(Charsets.UTF_8))
        out.write('\n'.code)
        out.flush()
    }

    /**
     * 带进度的流拷贝。
     * @return 是否完整拷贝了 total 字节（total<=0 表示读到 EOF 为止，进度按已读量上报）
     */
    fun copyWithProgress(
        input: InputStream,
        out: OutputStream,
        total: Long,
        onProgress: (Long) -> Unit,
    ): Boolean = try {
        val buf = ByteArray(BUF)
        var written = 0L
        while (true) {
            val n = if (total > 0L) {
                val want = minOf(buf.size.toLong(), total - written).toInt()
                if (want <= 0) break
                input.read(buf, 0, want)
            } else {
                input.read(buf)
            }
            if (n < 0) break
            out.write(buf, 0, n)
            written += n
            onProgress(written)
        }
        out.flush()
        total <= 0L || written == total
    } catch (e: Throwable) {
        Log.w(TAG, "copyWithProgress failed", e)
        false
    }

    /** 把输入流压缩成单条目 zip 文件（发送方「超过阈值先压缩」用） */
    fun zipToFile(input: InputStream, entryName: String, outFile: File): Boolean = try {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
            zos.setLevel(6)
            zos.putNextEntry(ZipEntry(entryName))
            input.copyTo(zos, BUF)
            zos.closeEntry()
        }
        true
    } catch (e: Throwable) {
        Log.w(TAG, "zipToFile failed", e)
        outFile.delete()
        false
    }

    /** 解压 zip 的第一个条目到目标目录，返回解出的文件（接收方「到手后解压」用） */
    fun unzipFirstEntry(zipFile: File, destDir: File): File? = try {
        if (!destDir.exists()) destDir.mkdirs()
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            val entry = zis.nextEntry ?: return null
            // 防路径穿越：只取条目文件名，丢掉任何目录成分
            val safeName = File(entry.name).name.ifEmpty { "payload.bin" }
            val out = File(destDir, safeName)
            FileOutputStream(out).use { fos -> zis.copyTo(fos, BUF) }
            out
        }
    } catch (e: Throwable) {
        Log.w(TAG, "unzipFirstEntry failed", e)
        null
    }
}

/**
 * 发送端：连接对端 → 发头 → 等 "OK" → 发负载（带进度）。
 *
 * @param headerJson 完整头 JSON（字符串）
 * @param openInput  延迟打开输入流（收到 OK 后再读文件，避免对方拒绝也白读一遍）
 * @param size       负载字节数（<=0 表示未知，发到 EOF 为止）
 */
internal object LanClient {
    suspend fun send(
        peer: LanPeer,
        headerJson: String,
        size: Long,
        openInput: () -> InputStream?,
        onProgress: (Long) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(peer.host, peer.port), 8000)
                socket.soTimeout = 15_000
                val out = socket.getOutputStream()
                val input = socket.getInputStream()
                LanTransfer.writeLine(out, headerJson)
                val reply = LanTransfer.readHeaderLine(input)
                if (reply != LanProtocol.REPLY_OK) {
                    throw java.io.IOException("对方拒绝了接收（$reply）")
                }
                val src = openInput() ?: throw java.io.IOException("源文件读取失败")
                src.use { s ->
                    socket.soTimeout = 60_000
                    val ok = LanTransfer.copyWithProgress(s, out, size, onProgress)
                    if (!ok) throw java.io.IOException("传输中断")
                }
                out.flush()
            }
            Unit
        }
    }
}

/**
 * 接收端服务：accept 循环，每个连接交给 [onConnection] 处理
 * （读头、应答、收负载的完整流程由 LanManager 组装，因为它要持有「待决定」状态）。
 */
internal class LanServer(
    private val onConnection: suspend (Socket) -> Unit,
) {
    private var server: ServerSocket? = null

    @Volatile
    private var running = false

    fun start(scope: CoroutineScope) {
        if (running) return
        running = true
        scope.launch(Dispatchers.IO) {
            try {
                val ss = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(LanProtocol.TCP_PORT))
                }
                server = ss
                while (running && !ss.isClosed) {
                    val conn = runCatching { ss.accept() }.getOrNull() ?: break
                    scope.launch(Dispatchers.IO) {
                        runCatching { onConnection(conn) }
                        runCatching { conn.close() }
                    }
                }
            } catch (e: Throwable) {
                Log.w("LanServer", "accept loop ended", e)
            }
        }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
    }
}
