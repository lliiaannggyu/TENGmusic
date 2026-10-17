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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer as graphicsLayerModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
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
 * TextStyle(brush=…)），未自绘 UI 控件。
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
 * 单行歌词：按所选切换动画用 graphicsLayer / TextStyle(brush) 做纯变换，
 * 不触发列表重测重排，杜绝逐句卡顿；满宽 + 居中保证水平居中；点击跳播。
 *
 * @param activeColor  当前句（高亮）歌词颜色：跟随专辑主色或用户自定义
 * @param anim         用户选择的切换动画
 * @param lineProgress 当前句内播放进度（0..1），供逐字填充 / 逐字显示使用
 */
@Composable
fun LyricLine(
    text: String,
    active: Boolean,
    activeColor: Color,
    anim: LyricTransition,
    lineProgress: Float = 1f,
    onClick: () -> Unit,
) {
    val dimColor = Color.Gray
    val p = lineProgress.coerceIn(0f, 1f)
    val density = LocalDensity.current

    when (anim) {
        LyricTransition.SCALE -> {
            val scale by animateFloatAsState(
                targetValue = if (active) 1.12f else 0.92f,
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
            Text(
                text = text,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp, horizontal = 12.dp)
                    .graphicsLayerModifier {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
            )
        }

        LyricTransition.FADE -> {
            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.45f,
                animationSpec = tween(durationMillis = 220),
                label = "lyricFade"
            )
            Text(
                text = text,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                color = if (active) activeColor else dimColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp, horizontal = 12.dp)
                    .graphicsLayerModifier { this.alpha = alpha }
            )
        }

        LyricTransition.SLIDE -> {
            // 成为当前行时：从下方 22dp 处滑入并淡入
            val ty by animateFloatAsState(
                targetValue = if (active) 0f else with(density) { 22.dp.toPx() },
                animationSpec = tween(durationMillis = 240),
                label = "lyricSlide"
            )
            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.45f,
                animationSpec = tween(durationMillis = 240),
                label = "lyricSlideAlpha"
            )
            Text(
                text = text,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                color = if (active) activeColor else dimColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp, horizontal = 12.dp)
                    .graphicsLayerModifier {
                        translationY = ty
                        this.alpha = alpha
                    }
            )
        }

        LyricTransition.KARAOKE -> {
            // 逐字填充：底层灰字 + 上层按句内进度裁宽的主题色字（遮罩法，
            // 规避 TextStyle(brush) 与 M3E Text 的 color 参数合并时被覆盖的问题）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    color = dimColor,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (active && p > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(p)
                            .fillMaxHeight()
                            .align(Alignment.CenterStart)
                            .clipToBounds(),
                    ) {
                        Text(
                            text = text,
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp,
                            color = activeColor,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        LyricTransition.TYPEWRITER -> {
            // 逐字显示：按句内进度截断字符数
            val shown = if (active) {
                (p * text.length).toInt().coerceIn(0, text.length)
            } else {
                text.length
            }
            Text(
                text = if (shown >= text.length) text else text.take(shown),
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                color = if (active) activeColor else dimColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp, horizontal = 12.dp)
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
            Text(
                text = text,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                color = if (active) activeColor else dimColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp, horizontal = 12.dp)
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
                    )
            )
        }

        LyricTransition.SPRING -> {
            // 弹性放大：spring 阻尼比小于 1，产生 Q 弹回弹
            val scale by animateFloatAsState(
                targetValue = if (active) 1.15f else 0.92f,
                animationSpec = spring(dampingRatio = 0.35f, stiffness = 900f),
                label = "lyricSpring"
            )
            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.45f,
                animationSpec = tween(durationMillis = 160),
                label = "lyricSpringAlpha"
            )
            Text(
                text = text,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                color = if (active) activeColor else dimColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp, horizontal = 12.dp)
                    .graphicsLayerModifier {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
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
            Text(
                text = text,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                color = if (active) activeColor else dimColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp, horizontal = 12.dp)
                    .graphicsLayerModifier {
                        rotationX = rot
                        cameraDistance = 12f * density.density
                        this.alpha = alpha
                    }
            )
        }
    }
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
            .height(30.dp)
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
