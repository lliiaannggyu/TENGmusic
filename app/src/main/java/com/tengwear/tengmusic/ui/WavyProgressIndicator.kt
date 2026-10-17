package com.tengwear.tengmusic.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * M3E 官方「波浪形进度条」高保真复刻（androidx.compose.material3 的
 * `LinearWavyProgressIndicator` 同款视觉）：
 *
 * - **轨道**：一条圆头横线（半透明），全宽；
 * - **活动条**：上缘为正弦波、下缘为直线的「丝带」，两端圆头——
 *   这就是 M3 Expressive 波浪进度条标志性的"水印丝带"形态；
 * - **波幅缩放**：0% 与 100% 处波幅归零（两端呈平线），中段满幅，
 *   与官方一致（官方在两端同样做渐入渐出，避免端点突兀）；
 * - 波长 / 波幅 / 条高均可调，默认值贴近 M3E 规范（条高 4dp、波幅 2.5dp、波长 26dp）。
 *
 * 说明：官方实现位于手机版 `androidx.compose.material3`（1.4+，ExperimentalMaterial3Api），
 * 本项目为纯 Wear 应用且离线构建无该构件，故按官方视觉规范以 Canvas 复刻，
 * 视觉与官方组件一致（非官方类同名，避免误导）。
 *
 * @param progress 0f ~ 1f；进度由调用方负责动画化（传入 animateFloatAsState 的值）
 * @param color 活动条颜色（播放器中跟随主题强调色）
 * @param trackColor 轨道颜色
 * @param amplitudeRampRatio 波幅渐入/渐出的进度占比（两端各 6% 内渐变为平线）
 */
@Composable
fun WavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    trackColor: Color = Color(0x33FFFFFF),
    barHeight: Dp = 4.dp,
    amplitude: Dp = 2.5.dp,
    wavelength: Dp = 26.dp,
    amplitudeRampRatio: Float = 0.06f,
    animate: Boolean = true,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 220, easing = LinearEasing),
        label = "wavy-progress",
    )
    val p = if (animate) animated else target

    Canvas(modifier = modifier) {
        val h = barHeight.toPx()
        val cy = size.height / 2f
        val wl = wavelength.toPx()
        // 波幅缩放：0% / 100% 处为 0（平线），中段满幅（官方同款端点渐入渐出）
        val ramp = amplitudeRampRatio.coerceIn(0.01f, 0.45f)
        val amp = amplitude.toPx() * ampScale(p, ramp)

        // 轨道：圆头横线，全宽
        drawLine(
            color = trackColor,
            start = Offset(0f, cy),
            end = Offset(size.width, cy),
            strokeWidth = h,
            cap = StrokeCap.Round,
        )
        if (p <= 0.001f) return@Canvas

        val w = size.width * p
        // 活动丝带：上缘正弦波（相位锚定左端，x=0 处位于中线），下缘平线
        val wave = Path()
        wave.moveTo(0f, cy)
        var x = 0f
        val stepPx = 1f
        while (x < w) {
            wave.lineTo(x, cy - amp * sin(2.0 * PI * x / wl).toFloat())
            x += stepPx
        }
        wave.lineTo(w, cy - amp * sin(2.0 * PI * w / wl).toFloat())
        wave.lineTo(w, cy + h / 2f)
        wave.lineTo(0f, cy + h / 2f)
        wave.close()
        drawPath(wave, color)
        // 两端圆头（Fill 无 stroke cap，用等宽圆补齐官方丝带的圆头效果）
        drawCircle(color = color, radius = h / 2f, center = Offset(0f, cy))
        drawCircle(
            color = color,
            radius = h / 2f,
            center = Offset(w, cy - amp * sin(2.0 * PI * w / wl).toFloat()),
        )
    }
}

/** 迷你预览版：固定 60% 进度，用于设置页样式选择的实时预览 */
@Composable
fun WavyProgressIndicatorPreview(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    trackColor: Color = Color(0x33FFFFFF),
) {
    WavyProgressIndicator(
        progress = 0.6f,
        modifier = modifier.width(64.dp).height(8.dp),
        color = color,
        trackColor = trackColor,
        animate = false,
    )
}

/**
 * 波幅缩放系数：进度进入两端各 [ramp] 区间内时，用 smoothstep 平滑过渡到 0，
 * 使 0% / 100% 处活动条呈平线（官方行为）；中段为 1（满幅）。
 */
private fun ampScale(p: Float, ramp: Float): Float {
    val inRamp = smoothstep((p / ramp).coerceIn(0f, 1f))
    val outRamp = smoothstep(((1f - p) / ramp).coerceIn(0f, 1f))
    return min(inRamp, outRamp)
}

private fun smoothstep(x: Float): Float = x * x * (3f - 2f * x)
