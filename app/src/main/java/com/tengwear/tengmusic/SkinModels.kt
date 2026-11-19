package com.tengwear.tengmusic

import org.json.JSONArray
import org.json.JSONObject

/**
 * v2 声明式皮肤引擎数据模型。
 *
 * 一个皮肤文件可以重新定义整个播放器页 / 歌词页的每一个控件：
 * 位置、尺寸、形状、颜色、显隐、字体、出现动画。换一个文件 = 重做一遍 UI。
 * 所有字段可选，缺省值让示例文件尽量精简。
 */

/** 解析 #RRGGBB / #AARRGGBB → ARGB Int */
internal fun skinColor(v: String?): Int? {
    if (v.isNullOrBlank()) return null
    var hex = v.trim().removePrefix("#")
    if (hex.length == 6) hex = "FF$hex"
    if (hex.length != 8) return null
    return try { hex.toLong(16).toInt() } catch (_: Exception) { null }
}

private fun jf(o: JSONObject, k: String, def: Float, min: Float, max: Float): Float =
    try { o.optDouble(k, def.toDouble()).toFloat().coerceIn(min, max) } catch (_: Exception) { def }
private fun ji(o: JSONObject, k: String, def: Int, min: Int, max: Int): Int =
    try { o.optInt(k, def).coerceIn(min, max) } catch (_: Exception) { def }
private fun jb(o: JSONObject, k: String, def: Boolean): Boolean =
    try { o.optBoolean(k, def) } catch (_: Exception) { def }
private fun js(o: JSONObject, k: String, def: String): String =
    try { o.optString(k, def) } catch (_: Exception) { def }
private fun jc(o: JSONObject, k: String): Int? =
    try { skinColor(o.optString(k, null)) } catch (_: Exception) { null }

/** 出现 / 消失动画规格 */
data class SkinAnim(
    val type: String,        // none/fade/slideUp/slideDown/slideLeft/slideRight/scale/pop/flip/blur/karaoke/typewriter
    val durMs: Int,
    val delayMs: Int,
    val distance: Float,     // dp，滑动距离
    val fromScale: Float,    // 缩放起点
    val easing: String,      // linear/easeIn/easeOut/easeInOut/spring
) {
    companion object {
        fun parse(o: JSONObject?, defType: String, defDur: Int): SkinAnim {
            if (o == null) return SkinAnim(defType, defDur, 0, 16f, 0.6f, "easeOut")
            return SkinAnim(
                type = js(o, "type", defType).lowercase(),
                durMs = ji(o, "dur", defDur, 0, 5000),
                delayMs = ji(o, "delay", 0, 0, 5000),
                distance = jf(o, "distance", 16f, 0f, 200f),
                fromScale = jf(o, "fromScale", 0.6f, 0f, 3f),
                easing = js(o, "easing", "easeOut").lowercase(),
            )
        }
    }
}

/** 歌词某一状态（高亮 / 非高亮）的文字样式 */
data class LyricTextStyle(
    val color: Int?,
    val size: Int,           // sp
    val bold: Boolean,
    val scale: Float,
    val opacity: Float,
    val glow: Boolean,
    val glowColor: Int?,
) {
    companion object {
        fun parse(o: JSONObject?, defColor: Int?, defSize: Int): LyricTextStyle {
            if (o == null) return LyricTextStyle(defColor, defSize, false, 1f, 1f, false, null)
            return LyricTextStyle(
                color = jc(o, "color") ?: defColor,
                size = ji(o, "size", defSize, 8, 40),
                bold = jb(o, "bold", false),
                scale = jf(o, "scale", 1f, 0.3f, 3f),
                opacity = jf(o, "opacity", 1f, 0f, 1f),
                glow = jb(o, "glow", false),
                glowColor = jc(o, "glowColor"),
            )
        }
    }
}

/** 播放器页单个控件样式 */
data class WidgetStyle(
    val id: String,
    val show: Boolean,
    val x: Float, val y: Float,          // 中心相对位置 0..1（相对内容正方形）
    val w: Float?, val h: Float?,        // 尺寸 dp；null = 用 frac 或 id 默认
    val frac: Float?,                    // 相对正方形边长比例（cover/progress 的直径）
    val shape: String,                   // circle/rounded/rect/none
    val radius: Float,                   // rounded 圆角比例 0..1
    val bg: Int?, val color: Int?,       // 背景 / 前景色
    val borderW: Float, val borderColor: Int?,
    val fontSize: Int?, val bold: Boolean,
    val align: String,                   // left/center/right
    val icon: String?,                   // 覆盖默认图标
    val opacity: Float,
    val rotate: Float,                   // 度
    val anim: SkinAnim,
    // 进度环专用
    val ringStyle: String,               // ring / wave
    val stroke: Float, val track: Int?, val disc: Int?,
    val waveAmp: Float, val waveLobes: Int, val waveSpeed: Int, val waveGap: Float,
) {
    /** 该控件实际使用的图标名（皮肤覆盖优先，否则按 id 默认） */
    val effectiveIcon: String get() = icon ?: when (id) {
        "play" -> "play"; "prev" -> "prev"; "next" -> "next"
        "volume" -> "volume"; "mode" -> "mode"; "library" -> "library"
        else -> ""
    }

    companion object {
        fun parse(o: JSONObject): WidgetStyle {
            val id = js(o, "id", "").lowercase()
            // 各控件的合理缺省
            val defX = when (id) { "prev" -> 0.28f; "next" -> 0.72f; else -> 0.5f }
            val idY = when (id) {
                "cover" -> 0.30f; "title" -> 0.61f; "artist" -> 0.68f
                "progress" -> 0.50f; "play" -> 0.84f; "prev" -> 0.84f; "next" -> 0.84f
                "volume" -> 0.95f; "mode" -> 0.95f; "library" -> 0.95f
                else -> 0.5f
            }
            val defFrac: Float? = when (id) { "cover" -> 0.46f; "progress" -> 0.90f; else -> null }
            val defW: Float = when (id) {
                "play" -> 60f; "prev" -> 42f; "next" -> 42f
                "volume" -> 30f; "mode" -> 30f; "library" -> 30f
                else -> 44f
            }
            val defShape = when (id) {
                "cover", "play", "prev", "next" -> "circle"
                "volume", "mode", "library" -> "none"
                else -> "none"
            }
            val defIcon = when (id) {
                "play" -> "play"; "prev" -> "prev"; "next" -> "next"
                "volume" -> "volume"; "mode" -> "mode"; "library" -> "library"
                else -> null
            }
            val defSize = when (id) { "title" -> 16; "artist" -> 11; else -> 16 }
            return WidgetStyle(
                id = id,
                show = jb(o, "show", true),
                x = jf(o, "x", defX, 0f, 1f),
                y = jf(o, "y", idY, 0f, 1f),
                w = if (o.has("w")) jf(o, "w", defW, 0f, 500f) else null,
                h = if (o.has("h")) jf(o, "h", defW, 0f, 500f) else null,
                frac = if (o.has("frac")) jf(o, "frac", defFrac ?: 0.4f, 0.05f, 1f) else defFrac,
                shape = js(o, "shape", defShape).lowercase(),
                radius = jf(o, "radius", 0.28f, 0f, 1f),
                bg = jc(o, "bg"), color = jc(o, "color"),
                borderW = jf(o, "borderW", 0f, 0f, 20f), borderColor = jc(o, "borderColor"),
                fontSize = if (o.has("fontSize")) ji(o, "fontSize", defSize, 8, 40) else defSize,
                bold = jb(o, "bold", id == "title"),
                align = js(o, "align", "center").lowercase(),
                icon = if (o.has("icon")) js(o, "icon", "") else defIcon,
                opacity = jf(o, "opacity", 1f, 0f, 1f),
                rotate = jf(o, "rotate", 0f, -360f, 360f),
                anim = SkinAnim.parse(if (o.has("anim")) o.optJSONObject("anim") else null, "fade", 320),
                ringStyle = js(o, "style", "ring").lowercase(),
                stroke = jf(o, "stroke", 6f, 1f, 40f),
                track = jc(o, "track"), disc = jc(o, "disc"),
                waveAmp = jf(o, "waveAmp", 0.048f, 0f, 0.3f),
                waveLobes = ji(o, "waveLobes", 10, 3, 30),
                waveSpeed = ji(o, "waveSpeed", 16000, 1000, 120000),
                waveGap = jf(o, "waveGap", 0.072f, 0f, 0.3f),
            )
        }
    }
}

/** 播放器皮肤 */
data class PlayerSkin(
    val name: String,
    val bg: Int?,
    val bgBlur: Float,
    val dim: Float,
    val widgets: List<WidgetStyle>,
) {
    companion object {
        fun fromJson(o: JSONObject): PlayerSkin {
            val arr: JSONArray? = try { o.optJSONArray("widgets") } catch (_: Exception) { null }
            val widgets = mutableListOf<WidgetStyle>()
            if (arr != null) for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                widgets.add(WidgetStyle.parse(item))
            }
            return PlayerSkin(
                name = js(o, "name", "未命名皮肤"),
                bg = jc(o, "bg"),
                bgBlur = jf(o, "bgBlur", 16f, 0f, 60f),
                dim = jf(o, "dim", 0.55f, 0f, 1f),
                widgets = widgets,
            )
        }
    }
}

/** 歌词皮肤 */
data class LyricsSkin(
    val name: String,
    val align: String,
    val lineGapDp: Float,
    val padDp: Float,
    val bgColor: Int?,
    val bgGlow: Int?,
    val bgGlowStrength: Float,
    val active: LyricTextStyle,
    val inactive: LyricTextStyle,
    val enter: SkinAnim,
    val exit: SkinAnim,
    val karaokeFill: Int?,
    val karaokeRest: Int?,
) {
    companion object {
        fun fromJson(o: JSONObject): LyricsSkin {
            val activeDef = LyricTextStyle(null, 18, true, 1.15f, 1f, false, null)
            val inactivateDef = LyricTextStyle(null, 14, false, 1f, 0.55f, false, null)
            return LyricsSkin(
                name = js(o, "name", "未命名歌词"),
                align = js(o, "align", "center").lowercase(),
                lineGapDp = jf(o, "lineGap", 18f, 0f, 80f),
                padDp = jf(o, "padding", 10f, 0f, 60f),
                bgColor = jc(o, "bgColor"),
                bgGlow = jc(o, "bgGlow"),
                bgGlowStrength = jf(o, "bgGlowStrength", 0.12f, 0f, 1f),
                active = LyricTextStyle.parse(
                    if (o.has("active")) o.optJSONObject("active") else null,
                    activeDef.color, activeDef.size,
                ),
                inactive = LyricTextStyle.parse(
                    if (o.has("inactive")) o.optJSONObject("inactive") else null,
                    inactivateDef.color, inactivateDef.size,
                ),
                enter = SkinAnim.parse(
                    if (o.has("enter")) o.optJSONObject("enter") else null, "slideUp", 320,
                ),
                exit = SkinAnim.parse(
                    if (o.has("exit")) o.optJSONObject("exit") else null, "fade", 220,
                ),
                karaokeFill = if (o.has("karaoke")) jc(o.optJSONObject("karaoke") ?: JSONObject(), "fill") else null,
                karaokeRest = if (o.has("karaoke")) jc(o.optJSONObject("karaoke") ?: JSONObject(), "rest") else null,
            )
        }
    }
}
