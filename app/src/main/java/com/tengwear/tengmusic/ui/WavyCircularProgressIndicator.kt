package com.tengwear.tengmusic.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * M3E 官方「圆形波浪进度条」高保真复刻（androidx.compose.material3 的
 * `CircularWavyProgressIndicator` 同款视觉，1.4.0-alpha18+ / @Material3ExpressiveApi）：
 *
 * - **轨道**：普通圆头弧线（半透明），铺满整个扫过角度；
 * - **进度弧**：沿弧线流动的正弦波（官方标志性形态），以每秒一个波长的速度
 *   沿播放方向流动（官方 waveSpeed 默认 = wavelength）；
 * - **振幅 ramp**（官方同款 `indicatorAmplitude`）：进度 ≤10% 或 ≥95% 时振幅为 0
 *   （呈平弧），10% ~ 95% 之间满幅，放大/缩小均带缓动过渡；
 * - **间隙**：进度弧末端与轨道起点之间留 gap（官方默认 4dp），两者不接触；
 * - **波长取整**：按「整个扫过角度内容纳整数个波」微调波长，保证 100% 时
 *   波形连续闭合（官方同样会为连续性调整波长）。
 *
 * 说明：官方实现位于手机版 androidx.compose.material3，Wear 组件库无此构件、
 * 离线构建也无法引入，故按官方规范以 Canvas 复刻（非官方类同名，避免误导）。
 *
 * @param progress 0f ~ 1f；由调用方负责动画化（传入 animateFloatAsState 的值）
 * @param color 进度弧颜色（播放器中跟随主题强调色）
 * @param trackColor 轨道颜色
 * @param startAngle 弧起点角度（Compose：0°=3点钟，顺时针增大）；默认 300°（1点钟）
 * @param sweepAngle 总扫过角度；默认 300°（顶部留 60° 缺口避让系统时钟 TimeText）
 * @param amplitude 波幅（半径方向最大位移）
 * @param wavelength 波长（会按整数波取整微调）
 * @param gapSize 进度弧与轨道之间的间隙（弧长方向）
 * @param animate 是否播放波形流动动画（设置页预览传 false）
 */
@Composable
fun WavyCircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    trackColor: Color = Color(0x33FFFFFF),
    strokeWidth: Dp = 4.dp,
    trackStrokeWidth: Dp = 4.dp,
    amplitude: Dp = 3.dp,
    wavelength: Dp = 40.dp,
    gapSize: Dp = 4.dp,
    startAngle: Float = 300f,
    sweepAngle: Float = 300f,
    animate: Boolean = true,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 220, easing = LinearEasing),
        label = "wavy-circular-progress",
    )
    val p = if (animate) animated else target

    // 官方振幅 ramp：进度 ≤10% / ≥95% 时为 0（平弧），中段满幅；带缓动过渡
    val ampTarget = if (p <= 0.1f || p >= 0.95f) 0f else 1f
    val ampFactor by animateFloatAsState(
        targetValue = ampTarget,
        animationSpec = tween(durationMillis = 450, easing = LinearEasing),
        label = "wavy-amp",
    )

    // 波形相位：每秒流动一个波长（官方 waveSpeed 默认 = wavelength）
    val infinite = rememberInfiniteTransition(label = "wavy-phase")
    val phaseT by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val phase = if (animate) phaseT * 2f * PI.toFloat() else 0f

    Canvas(modifier = modifier) {
        val strokePx = strokeWidth.toPx()
        val trackStrokePx = trackStrokeWidth.toPx()
        val ampPx = amplitude.toPx() * ampFactor
        val cx = size.width / 2f
        val cy = size.height / 2f
        // 半径内缩：保证波峰（r+amp）与描边都不越出 Canvas 边界被裁剪
        val r = minOf(size.width, size.height) / 2f -
            minOf(strokePx, trackStrokePx) / 2f - ampPx
        if (r <= 0f) return@Canvas

        // —— 轨道：进度弧之后的剩余弧（留出 gap，与进度弧不接触）——
        val gapDeg = Math.toDegrees((gapSize.toPx() / r).toDouble()).toFloat()
        val trackStartDeg = startAngle + sweepAngle * p + gapDeg
        val trackSweepDeg = sweepAngle * (1f - p) - gapDeg
        if (trackSweepDeg > 0.01f) {
            drawArc(
                color = trackColor,
                startAngle = trackStartDeg,
                sweepAngle = trackSweepDeg,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = trackStrokePx, cap = StrokeCap.Round),
            )
        }
        if (p <= 0.001f) return@Canvas

        val progressSweepDeg = sweepAngle * p
        if (ampPx <= 0.01f) {
            // 平弧（官方：进度进入两端区域时振幅归零）
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = progressSweepDeg,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            return@Canvas
        }

        // —— 进度弧：正弦波沿弧线流动 ——
        val startRad = startAngle * PI.toFloat() / 180f
        val progressSweepRad = progressSweepDeg * PI.toFloat() / 180f
        val fullArcLen = sweepAngle * PI.toFloat() / 180f * r
        // 波长取整：整个扫过角度内容纳整数个波，100% 时波形连续闭合
        val cycles = (fullArcLen / wavelength.toPx()).roundToInt().coerceAtLeast(1)
        val wlAdj = fullArcLen / cycles
        val progressArcLen = progressSweepRad * r
        val segments = (progressArcLen / 1.5f).roundToInt().coerceIn(48, 720)
        val path = Path()
        for (i in 0..segments) {
            val t = i.toFloat() / segments
            val s = t * progressArcLen
            val angle = startRad + t * progressSweepRad
            val radius = r + ampPx * sin(2f * PI.toFloat() * s / wlAdj - phase).toFloat()
            val pt = Offset(cx + radius * cos(angle), cy + radius * sin(angle))
            if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }
}

/** 迷你预览版：固定 60% 进度、静止波形，用于设置页样式选择的实时预览 */
@Composable
fun WavyCircularProgressIndicatorPreview(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    trackColor: Color = Color(0x33FFFFFF),
) {
    WavyCircularProgressIndicator(
        progress = 0.6f,
        modifier = modifier.size(26.dp),
        color = color,
        trackColor = trackColor,
        strokeWidth = 3.dp,
        trackStrokeWidth = 3.dp,
        amplitude = 2.dp,
        wavelength = 40.dp,
        gapSize = 2.dp,
        animate = false,
    )
}
