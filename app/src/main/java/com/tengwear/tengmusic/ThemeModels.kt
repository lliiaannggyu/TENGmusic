package com.tengwear.tengmusic

import org.json.JSONObject
import com.tengwear.tengmusic.ui.LyricTransition

/**
 * 自定义主题文件的数据模型（v1.48）。
 *
 * 主题是用户自己编写的 .json 文件，通过应用内「主题工坊」导入。
 * 分两类：播放器 UI（kind="player"）与歌词（kind="lyrics"）。
 * 字段全部可选——缺失的字段沿用应用默认值，方便用户只改想改的项。
 *
 * 颜色格式：#RRGGBB 或 #AARRGGBB（不带 # 的 6/8 位十六进制也可）。
 */

/** 解析 #RRGGBB / #AARRGGBB → Android ARGB Int；失败返回 null */
private fun parseColor(value: String?): Int? {
    if (value.isNullOrBlank()) return null
    var hex = value.trim().removePrefix("#")
    if (hex.length == 6) hex = "FF$hex"
    if (hex.length != 8) return null
    return try {
        hex.toLong(16).toInt()
    } catch (_: Exception) {
        null
    }
}

private fun JSONObject.optColor(key: String): Int? = parseColor(optString(key, null))
private fun JSONObject.optFloatIn(key: String, def: Float, min: Float, max: Float): Float =
    (optDouble(key, def.toDouble()).toFloat()).coerceIn(min, max)
private fun JSONObject.optIntIn(key: String, def: Int, min: Int, max: Int): Int =
    optInt(key, def).coerceIn(min, max)

/** 播放器 UI 主题 */
data class PlayerTheme(
    val name: String,
    val accent: Int?,
    val background: Int?,
    val titleSizeSp: Int?,
    val artistSizeSp: Int?,
    val ringRadiusFrac: Float?,
    val ringStrokeDp: Float?,
    val progressStyle: Int?,
    val blurDp: Float?,
    val dimAlpha: Float?,
) {
    companion object {
        fun fromJson(obj: JSONObject): PlayerTheme = PlayerTheme(
            name = obj.optString("name", "未命名主题"),
            accent = obj.optColor("accent"),
            background = obj.optColor("background"),
            titleSizeSp = if (obj.has("titleSize")) obj.optIntIn("titleSize", 16, 10, 28) else null,
            artistSizeSp = if (obj.has("artistSize")) obj.optIntIn("artistSize", 11, 8, 20) else null,
            ringRadiusFrac = if (obj.has("ringRadius")) obj.optFloatIn("ringRadius", 0.47f, 0.05f, 0.50f) else null,
            ringStrokeDp = if (obj.has("ringStroke")) obj.optFloatIn("ringStroke", 5f, 1f, 24f) else null,
            progressStyle = if (obj.has("progressStyle")) obj.optIntIn("progressStyle", 0, 0, 1) else null,
            blurDp = if (obj.has("blur")) obj.optFloatIn("blur", 16f, 0f, 40f) else null,
            dimAlpha = if (obj.has("dim")) obj.optFloatIn("dim", 0.55f, 0f, 1f) else null,
        )
    }
}

/** 歌词主题 */
data class LyricsTheme(
    val name: String,
    val activeColor: Int?,
    val dimColor: Int?,
    val fontSizeSp: Int?,
    val bgGlow: Int?,
    val transitionId: Int?,
) {
    companion object {
        fun fromJson(obj: JSONObject): LyricsTheme = LyricsTheme(
            name = obj.optString("name", "未命名主题"),
            activeColor = obj.optColor("activeColor"),
            dimColor = obj.optColor("dimColor"),
            fontSizeSp = if (obj.has("fontSize")) obj.optIntIn("fontSize", 15, 12, 26) else null,
            bgGlow = obj.optColor("bgGlow"),
            transitionId = if (obj.has("transition")) {
                obj.optIntIn("transition", 0, 0, LyricTransition.entries.size - 1)
            } else null,
        )
    }
}

/** 一份导入的主题文件（含原始 JSON，便于重新应用） */
data class ThemeFile(
    val fileName: String,
    val kind: String,          // "player" / "lyrics" / "unknown"
    val name: String,
    val rawJson: String,
    val player: PlayerTheme?,  // v1 精简主题
    val lyrics: LyricsTheme?,
    val playerSkin: PlayerSkin?,   // v2 声明式皮肤（version >= 2）
    val lyricsSkin: LyricsSkin?,
    val version: Int,
) {
    /** 是否 v2 声明式皮肤 */
    val isSkin: Boolean get() = version >= 2 && (playerSkin != null || lyricsSkin != null)

    companion object {
        fun parse(fileName: String, raw: String): ThemeFile? {
            val obj = try {
                JSONObject(raw)
            } catch (_: Exception) {
                return null
            }
            val kind = obj.optString("kind", "").lowercase()
            val version = obj.optInt("version", 1)
            val name = obj.optString("name", fileName.removeSuffix(".json"))
            val player: PlayerTheme?
            val lyrics: LyricsTheme?
            val playerSkin: PlayerSkin?
            val lyricsSkin: LyricsSkin?
            if (version >= 2) {
                // v2：声明式皮肤
                playerSkin = if (kind == "player") PlayerSkin.fromJson(obj) else null
                lyricsSkin = if (kind == "lyrics") LyricsSkin.fromJson(obj) else null
                player = null
                lyrics = null
            } else {
                // v1：精简主题（向下兼容旧文件）
                player = if (kind == "player") PlayerTheme.fromJson(obj) else null
                lyrics = if (kind == "lyrics") LyricsTheme.fromJson(obj) else null
                playerSkin = null
                lyricsSkin = null
            }
            val valid = (player != null || lyrics != null || playerSkin != null || lyricsSkin != null)
            return ThemeFile(
                fileName = fileName,
                kind = if (player != null || playerSkin != null) "player"
                       else if (lyrics != null || lyricsSkin != null) "lyrics" else "unknown",
                name = name,
                rawJson = raw,
                player = player,
                lyrics = lyrics,
                playerSkin = playerSkin,
                lyricsSkin = lyricsSkin,
                version = version,
            ).let { if (valid) it else null }
        }
    }
}
