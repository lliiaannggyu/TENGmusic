package com.tengwear.tengmusic

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * 极简 ID3v2.3 标签写入器——「封面工具」的底层实现。
 *
 * 功能：给 MP3 音频文件写入 / 替换专辑封面（APIC 帧），
 * 同时重建并保留标题（TIT2）/ 艺术家（TPE1）/ 专辑（TALB）文本帧。
 *
 * 写入策略（保证不损坏音频数据）：
 *  1. 通过 SAF 读入原文件全量字节；
 *  2. 剥离文件头部的旧 ID3v2 标签（若有），保留纯净音频帧数据；
 *  3. 用新帧重建一个 ID3v2.3 标签，拼回音频数据前；
 *  4. 以「w」模式一次性覆盖写回（truncate + 全量写，无中间态）。
 *
 * 兼容性：仅处理 ID3v2.2/v2.3/v2.4 头部剥离（按头部长度跳过），
 * 文件尾部的 ID3v1 标签不受影响。非 MP3 文件直接拒绝，避免写坏。
 */
object Id3Tag {

    /** @return (是否成功, 结果描述) */
    fun writeCoverArt(
        context: Context,
        uri: Uri,
        imageBytes: ByteArray,
        mime: String,
        title: String = "",
        artist: String = "",
        album: String = "",
    ): Pair<Boolean, String> {
        return try {
            // 1) 读原文件全量字节
            val original = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return false to "无法读取文件"
            if (original.size < 4) return false to "文件过小，不像音频文件"

            // 2) 剥离旧 ID3v2
            val audio = stripId3v2(original)

            // 3) 校验 MPEG 帧同步（剥离 ID3 后首字节应为 0xFF Ex/Ex，避免把 FLAC/M4A 写坏）
            if (audio.isEmpty() || audio[0] != 0xFF.toByte() || (audio[1].toInt() and 0xE0) != 0xE0) {
                return false to "仅支持 MP3 文件（未识别到 MPEG 帧头）"
            }

            // 4) 重建标签 + 音频数据，一次性覆盖写回
            val out = buildTag(title, artist, album, imageBytes, mime) + audio
            context.contentResolver.openOutputStream(uri, "w")?.use { it.write(out) }
                ?: return false to "无法写入文件（无写权限）"
            true to "OK"
        } catch (e: Throwable) {
            false to (e.message ?: e.javaClass.simpleName)
        }
    }

    /** 剥离头部 ID3v2 标签，返回纯音频字节 */
    private fun stripId3v2(data: ByteArray): ByteArray {
        if (data.size < 10) return data
        if (!(data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == '3'.code.toByte())) {
            return data
        }
        val flags = data[5].toInt()
        // v2.2/v2.3/v2.4 的标签体大小都是 28 位 syncsafe 整数（v2.2 略有差异，但极少见，按同构处理）
        val bodySize =
            ((data[6].toInt() and 0x7F) shl 21) or
                ((data[7].toInt() and 0x7F) shl 14) or
                ((data[8].toInt() and 0x7F) shl 7) or
                (data[9].toInt() and 0x7F)
        var total = 10 + bodySize
        // 有 footer（v2.4 扩展）时再 +10
        if (flags and 0x10 != 0) total += 10
        if (total >= data.size) return data.copyOfRange(0, 0) // 异常：标签声称比文件还大
        return data.copyOfRange(total, data.size)
    }

    /** 构建 ID3v2.3 标签字节 */
    private fun buildTag(
        title: String,
        artist: String,
        album: String,
        imageBytes: ByteArray,
        mime: String,
    ): ByteArray {
        val body = ByteArrayOutputStream()
        textFrame("TIT2", title)?.let { body.write(it) }
        textFrame("TPE1", artist)?.let { body.write(it) }
        textFrame("TALB", album)?.let { body.write(it) }
        body.write(apicFrame(mime, imageBytes))

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()))
        out.write(byteArrayOf(3, 0)) // v2.3
        out.write(byteArrayOf(0)) // flags
        out.write(syncsafe(body.size()))
        out.write(body.toByteArray())
        return out.toByteArray()
    }

    /** 文本帧：UTF-16（带 BOM）编码，保证中文不乱码 */
    private fun textFrame(id: String, text: String): ByteArray? {
        if (text.isEmpty()) return null
        val data = ByteArrayOutputStream()
        data.write(0x01) // 0x01 = UTF-16 with BOM
        data.write(0xFF)
        data.write(0xFE)
        data.write(text.toByteArray(Charsets.UTF_16LE))
        return frame(id, data.toByteArray())
    }

    /** APIC 封面帧：MIME 用 ISO-8859-1，图片类型 3 = 前置封面 */
    private fun apicFrame(mime: String, data: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(0x00) // ISO-8859-1 编码 MIME 与描述
        body.write(mime.toByteArray(Charsets.ISO_8859_1))
        body.write(0)
        body.write(0x03) // Picture type: 3 = front cover
        body.write(0) // 空描述
        body.write(data)
        return frame("APIC", body.toByteArray())
    }

    /** ID3v2.3 帧：帧 ID(4) + 数据长度(4, 大端非 syncsafe) + 标志(2) + 数据 */
    private fun frame(id: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(
            ((data.size shr 24) and 0xFF).toByte(),
            ((data.size shr 16) and 0xFF).toByte(),
            ((data.size shr 8) and 0xFF).toByte(),
            (data.size and 0xFF).toByte(),
        ))
        out.write(byteArrayOf(0, 0))
        out.write(data)
        return out.toByteArray()
    }

    /** 28 位 syncsafe 整数（每个字节最高位为 0） */
    private fun syncsafe(v: Int): ByteArray = byteArrayOf(
        ((v shr 21) and 0x7F).toByte(),
        ((v shr 14) and 0x7F).toByte(),
        ((v shr 7) and 0x7F).toByte(),
        (v and 0x7F).toByte(),
    )
}
