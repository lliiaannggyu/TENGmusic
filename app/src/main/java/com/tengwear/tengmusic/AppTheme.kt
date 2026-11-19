package com.tengwear.tengmusic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.graphics.Color

/**
 * 全局主题强调色：所有新增页面（文件管理器、在线搜索、主题工坊、专辑详情等）
 * 统一从这里取色，跟随用户当前的主题色（专辑封面主色 / 自定义色 / v2 皮肤色），
 * 不再各自写死蓝色。
 *
 * 由 PlayerViewModel 在 recomputeLyricColor() / applyTheme() 时同步更新。
 */
object AppTheme {
    private val _accent = MutableStateFlow(DEFAULT_ACCENT)
    val accent: StateFlow<Int> = _accent.asStateFlow()

    /** 默认青色（无主题时的兜底，与历史观感一致） */
    const val DEFAULT_ACCENT = 0xFF34C7F4.toInt()

    fun update(color: Int) {
        _accent.value = color
    }

    /** 带透明度的强调色：alpha 用 0x00~0xFF 的 Int（0~255） */
    fun withAlpha(color: Int, alpha: Int): Color =
        Color(android.graphics.Color.argb(alpha.coerceIn(0, 255),
            android.graphics.Color.red(color),
            android.graphics.Color.green(color),
            android.graphics.Color.blue(color)))
}
