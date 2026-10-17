package com.tengwear.tengmusic.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer as graphicsLayerModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay

/**
 * 歌词切换动画类型（用户可在设置里自定义选择，设置项带实时预览）。
 *
 * 动画思路参考自 GitHub 高星歌词控件项目（均已在开源致谢中列出）：
 * - markzhai/LyricView（属性动画逐行滚动）、wangchenyan/lrcview（平滑滚动 + 缩放高亮）、
 * - douzifly/android-lrc-view（手势 seek + 切换）、asr-pub/LyricViewDemo（逐字高亮）、
 * - Replica0110/Lyrico（逐字歌词）等。
 *
 * 实现全部使用 Jetpack Compose 官方动画 API（animateFloatAsState / spring / graphicsLayer /
 * AnnotatedString SpanStyle），未自绘 UI 控件。
 *
 * 手表适配要点（圆屏 + 小字号）：
 * - 每行固定「两行小字」高度提前占位：小字放得下的句子也按两行预留空间，
 *   唱到长句（自动换两行）时布局不变，切换不跳不动；
 * - 逐字填充/逐字显示按「字符」截断（不再按整行宽度遮罩），多行歌词第二行不会被裁掉；
 * - 位移/缩放心智收敛到小幅度（上滑 12dp、缩放 1.1x），避免圆屏上动画过冲。
 */
enum class LyricTransition(val label: String, val desc: String) {
    /** 经典：当前行放大高亮 */
    SCALE("缩放高亮", "当前行放大，其余行变暗"),

    /** 柔和淡入淡出 */
    FADE("淡入淡出", "透明度柔和过渡"),

    /** 从下方滑入并淡入 */
    SLIDE("上滑入场", "当前行从下滑入并淡入"),

    /** 按句内播放进度逐字填充点亮 */
    KARAOKE("逐字填充", "按播放进度逐字点亮"),

    /** 按句内播放进度逐字打出 */
    TYPEWRITER("逐字显示", "随播放进度逐字打出"),

    /** 由模糊到清晰的渐显 */
    BLUR("模糊渐显", "由模糊慢慢变清晰"),

    /** Q 弹弹性缩放 */
    SPRING("弹性放大", "Q 弹的弹性缩放"),

    /** 3D 翻牌 */
    FLIP("立体翻转", "3D 翻牌式切换"),
    ;

    companion object {
        fun fromId(id: Int): LyricTransition = entries.getOrElse(id) { SCALE }
    }
}

/**
 * 单行歌词：按所选切换动画用 graphicsLayer / AnnotatedString 做纯变换，
 * 不触发列表重测重排，杜绝逐句卡顿；满宽 + 居中保证水平居中；点击跳播。
 *
 * 布局占位（修复「小字刚好放下 → 长句变两行 → 切换生硬」）：
 * 外层 Box 固定 minHeight = 两行小字高度（字号 × 2.6 + 上下内边距），
 * 无论当前是一行还是两行都先占好两行空间；active 态的放大/位移只作用于
 * graphicsLayer（绘制层），不影响测量结果，因此句子变长也不会把列表顶跳。
 *
 * @param activeColor  当前句（高亮）歌词颜色：跟随专辑主色或用户自定义
 * @param anim         用户选择的切换动画
 * @param lineProgress 当前句内播放进度（0..1），供逐字填充 / 逐字显示使用
 * @param fontSize     歌词字号（设置里可调，12sp ~ 22sp）
 */
@Composable
fun LyricLine(
    text: String,
    active: Boolean,
    activeColor: Color,
    anim: LyricTransition,
    lineProgress: Float = 1f,
    fontSize: TextUnit = 15.sp,
    onClick: () -> Unit,
) {
    val dimColor = Color.Gray
    val p = lineProgress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    // 两行小字占位高度：字号 × 2（两行）× 1.3（行高）+ 12dp 上下内边距
    val twoLineMinH = with(density) { (fontSize.value * 2.6f + 12f).dp }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = twoLineMinH)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LyricLineContent(
            text = text,
            active = active,
            activeColor = activeColor,
            anim = anim,
            p = p,
            dimColor = dimColor,
            fontSize = fontSize,
            density = density,
        )
    }
}

/** 动画内容本体（外层占位见 [LyricLine]） */
@Composable
private fun LyricLineContent(
    text: String,
    active: Boolean,
    activeColor: Color,
    anim: LyricTransition,
    p: Float,
    dimColor: Color,
    fontSize: TextUnit,
    density: androidx.compose.ui.unit.Density,
) {
    when (anim) {
        LyricTransition.SCALE -> {
            val scale by animateFloatAsState(
                targetValue = if (active) 1.10f else 0.94f,
                animationSpec = tween(durationMillis = 160),
                label = "lyricScale"
            )
            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.45f,
                animationSpec = tween(durationMillis = 160),
                label = "lyricAlpha"
            )
            val color by animateColorAsState(
                targetValue = if (active) activeColor else dimColor,
                animationSpec = tween(durationMillis = 160),
                label = "lyricColor"
            )
            LyricText(
                text = text,
                fontSize = fontSize,
                color = color,
                modifier = Modifier.graphicsLayerModifier {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            )
        }

        LyricTransition.FADE -> {
            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.45f,
                animationSpec = tween(durationMillis = 220),
                label = "lyricFade"
            )
            LyricText(
                text = text,
                fontSize = fontSize,
                color = if (active) activeColor else dimColor,
                modifier = Modifier.graphicsLayerModifier { this.alpha = alpha },
            )
        }

        LyricTransition.SLIDE -> {
            // 成为当前行时：从下方 12dp 处滑入并淡入（手表小屏收窄位移，避免过冲出界）
            val ty by animateFloatAsState(
                targetValue = if (active) 0f else with(density) { 12.dp.toPx() },
                animationSpec = tween(durationMillis = 240),
                label = "lyricSlide"
            )
            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.45f,
                animationSpec = tween(durationMillis = 240),
                label = "lyricSlideAlpha"
            )
            LyricText(
                text = text,
                fontSize = fontSize,
                color = if (active) activeColor else dimColor,
                modifier = Modifier.graphicsLayerModifier {
                    translationY = ty
                    this.alpha = alpha
                },
            )
        }

        LyricTransition.KARAOKE -> {
            // 逐字填充：同一段文本按句内进度染色（已唱 = 主题色，未唱 = 灰色）。
            // 用 AnnotatedString SpanStyle 逐字符上色（而非整行宽度遮罩），
            // 多行歌词的第二行也能正常逐字点亮，不会被裁掉。
            val shown = (p * text.length).toInt().coerceIn(0, text.length)
            val styled: AnnotatedString = when {
                active && shown in 1 until text.length -> buildAnnotatedString {
                    append(text)
                    addStyle(SpanStyle(color = activeColor), 0, shown)
                    addStyle(SpanStyle(color = dimColor), shown, text.length)
                }
                else -> buildAnnotatedString {
                    withStyle(SpanStyle(color = if (active) activeColor else dimColor)) {
                        append(text)
                    }
                }
            }
            Text(
                text = styled,
                textAlign = TextAlign.Center,
                fontSize = fontSize,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LyricTransition.TYPEWRITER -> {
            // 逐字显示：按句内进度截断字符数（字符截断天然支持多行，
            // 换行符 \n 也计入字符，第二行不会被整段裁掉）
            val shown = if (active) {
                (p * text.length).toInt().coerceIn(0, text.length)
            } else {
                text.length
            }
            LyricText(
                text = if (shown >= text.length) text else text.take(shown),
                fontSize = fontSize,
                color = if (active) activeColor else dimColor,
            )
        }

        LyricTransition.BLUR -> {
            // 模糊渐显：当前行由模糊（约 10dp）渐变到清晰
            val t by animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = tween(durationMillis = 420),
                label = "lyricBlur"
            )
            val blurPx = with(density) { (1f - t) * 10.dp.toPx() }
            val alpha = (0.45f + 0.55f * t)
            val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            LyricText(
                text = text,
                fontSize = fontSize,
                color = if (active) activeColor else dimColor,
                modifier = Modifier
                    .then(
                        if (canBlur) {
                            Modifier.graphicsLayerModifier {
                                renderEffect = RenderEffect
                                    .createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                                    .asComposeRenderEffect()
                                this.alpha = alpha
                            }
                        } else {
                            Modifier.graphicsLayerModifier { this.alpha = alpha }
                        }
                    ),
            )
        }

        LyricTransition.SPRING -> {
            // 弹性放大：spring 阻尼比 0.5（弱阻尼但不过冲），小屏上幅度收敛到 1.10x
            val scale by animateFloatAsState(
                targetValue = if (active) 1.10f else 0.94f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 900f),
                label = "lyricSpring"
            )
            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.45f,
                animationSpec = tween(durationMillis = 160),
                label = "lyricSpringAlpha"
            )
            LyricText(
                text = text,
                fontSize = fontSize,
                color = if (active) activeColor else dimColor,
                modifier = Modifier.graphicsLayerModifier {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            )
        }

        LyricTransition.FLIP -> {
            // 3D 翻牌：成为当前行时绕 X 轴从 90° 翻到 0°
            val rot by animateFloatAsState(
                targetValue = if (active) 0f else 90f,
                animationSpec = tween(durationMillis = 260),
                label = "lyricFlip"
            )
            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.45f,
                animationSpec = tween(durationMillis = 200),
                label = "lyricFlipAlpha"
            )
            LyricText(
                text = text,
                fontSize = fontSize,
                color = if (active) activeColor else dimColor,
                modifier = Modifier.graphicsLayerModifier {
                    rotationX = rot
                    cameraDistance = 12f * density.density
                    this.alpha = alpha
                },
            )
        }
    }
}

/** 歌词文本统一样式：满宽居中、最多两行（占位高度保证两行也不跳动） */
@Composable
private fun LyricText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        fontSize = fontSize,
        color = color,
        maxLines = 2,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 设置页里的歌词动画实时预览：迷你歌词行循环演示该动画的切换效果。
 * 每轮：点亮并推进句内进度（1.5s）→ 熄灭（0.5s）→ 循环。
 */
@Composable
fun LyricTransitionPreview(anim: LyricTransition, accent: Color) {
    var active by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(anim) {
        while (true) {
            active = true
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1500, easing = LinearEasing),
            ) { v, _ -> progress = v }
            delay(500)
            active = false
            progress = 0f
            delay(500)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .height(34.dp)
            .clip(CircleShape)
            .background(Color(0x22FFFFFF)),
        contentAlignment = Alignment.Center,
    ) {
        LyricLine(
            text = "歌词切换预览",
            active = active,
            activeColor = accent,
            anim = anim,
            lineProgress = if (active) progress else 0f,
            onClick = {},
        )
    }
}
