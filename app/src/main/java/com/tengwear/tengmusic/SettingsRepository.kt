package com.tengwear.tengmusic

import android.content.Context

/**
 * 播放器通用设置（歌词颜色等）的本地持久化。
 * 与 FavoritesRepository 类似，统一用 SharedPreferences 存储。
 */
object SettingsRepository {
    private const val PREF = "tengmusic_settings"

    /** 歌词颜色模式：0 = 跟随专辑封面主色，1 = 使用用户自定义色 */
    private const val KEY_LYRIC_COLOR_MODE = "lyric_color_mode"

    /** 自定义歌词颜色（0xAARRGGBB） */
    private const val KEY_LYRIC_CUSTOM_COLOR = "lyric_custom_color"

    /** 播放器 UI 主题：0 = 经典（白色半透明胶囊），1 = M3E（底部三瓣长胶囊） */
    private const val KEY_UI_THEME = "ui_theme"

    /** 进度环半径占屏幕短边比例（0.05 ~ 0.50；0.50 时圆环直径正好等于屏幕短边，即顶到圆屏边缘，为物理上限；0.05 可缩到很小） */
    private const val KEY_RING_RADIUS_FRAC = "ring_radius_frac"

    /** 进度环描边粗细（dp） */
    private const val KEY_RING_STROKE = "ring_stroke"

    /** 歌词切换动画（LyricTransition.ordinal） */
    private const val KEY_LYRIC_TRANSITION = "lyric_transition"

    /** 歌词字号（sp，12 ~ 22，默认 15） */
    private const val KEY_LYRIC_FONT_SIZE = "lyric_font_size"

    fun getLyricColorMode(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_LYRIC_COLOR_MODE, 0)

    fun getUiTheme(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_UI_THEME, 0)

    fun getRingRadiusFrac(context: Context): Float =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getFloat(KEY_RING_RADIUS_FRAC, 0.47f)
            // 读取时也钳制：旧版本可能存过超出可见范围的值，迁移时拉回可见范围
            .coerceIn(0.05f, 0.50f)

    fun getRingStroke(context: Context): Float =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getFloat(KEY_RING_STROKE, 5f)

    fun getLyricCustomColor(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_LYRIC_CUSTOM_COLOR, 0xFFFFFFFF.toInt())

    fun setLyricColorMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LYRIC_COLOR_MODE, mode)
            .apply()
    }

    fun setLyricCustomColor(context: Context, color: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LYRIC_CUSTOM_COLOR, color)
            .apply()
    }

    fun setUiTheme(context: Context, theme: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_UI_THEME, theme)
            .apply()
    }

    fun setRingRadiusFrac(context: Context, frac: Float) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_RING_RADIUS_FRAC, frac.coerceIn(0.05f, 0.50f))
            .apply()
    }

    fun setRingStroke(context: Context, dp: Float) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_RING_STROKE, dp.coerceIn(2f, 12f))
            .apply()
    }

    fun getLyricTransition(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_LYRIC_TRANSITION, 0)

    fun setLyricTransition(context: Context, id: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LYRIC_TRANSITION, id)
            .apply()
    }

    /** 歌词字号（sp），读取时钳制在手表可读范围 12 ~ 22 */
    fun getLyricFontSize(context: Context): Float =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getFloat(KEY_LYRIC_FONT_SIZE, 15f)
            .coerceIn(12f, 22f)

    fun setLyricFontSize(context: Context, sp: Float) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_LYRIC_FONT_SIZE, sp.coerceIn(12f, 22f))
            .apply()
    }
}
