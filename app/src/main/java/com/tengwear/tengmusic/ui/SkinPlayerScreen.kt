package com.tengwear.tengmusic.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.tengwear.tengmusic.PlayerViewModel
import com.tengwear.tengmusic.PlayMode
import com.tengwear.tengmusic.SkinAnim
import com.tengwear.tengmusic.WidgetStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * v2 声明式播放器皮肤渲染引擎。
 *
 * 按 [PlayerSkin.widgets] 的配置，把每个控件绝对定位到内容正方形内：
 *  - x/y：控件中心相对位置（0..1）
 *  - w/h 或 frac：尺寸
 *  - shape/bg/color/fontSize/...：外观
 *  - anim：出现动画（按 widgets 顺序 stagger 依次入场）
 * 换一份皮肤文件 = 整个 UI 重做。
 */

/** 字符串 easing → AnimationSpec<Float> */
private fun animSpec(a: SkinAnim): AnimationSpec<Float> = when (a.easing) {
    "linear" -> tween(a.durMs, a.delayMs, LinearEasing)
    "easein" -> tween(a.durMs, a.delayMs, FastOutLinearInEasing)
    "easeinout" -> tween(a.durMs, a.delayMs, FastOutSlowInEasing)
    "spring" -> spring(dampingRatio = 0.5f, stiffness = 300f)
    else -> tween(a.durMs, a.delayMs, LinearOutSlowInEasing) // easeOut
}

@Composable
fun SkinPlayerScreen(
    vm: PlayerViewModel,
    skin: com.tengwear.tengmusic.PlayerSkin,
    active: Boolean = true,
    onOpenVolume: (() -> Unit)? = null,
) {
    val tracks by vm.tracks.collectAsState()
    val index by vm.currentIndex.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val position by vm.position.collectAsState()
    val duration by vm.duration.collectAsState()
    val playMode by vm.playMode.collectAsState()
    val current = tracks.getOrNull(index)
    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "skinProgress")

    val density = LocalDensity.current
    val themeColor = Color(android.graphics.Color.parseColor("#34C7F4"))

    // v1.67：波浪自转动画已删除（性能优先），波浪盘静止（phase 恒 0）。
    val phase = 0f

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val S: Dp = minOf(maxWidth, maxHeight)

        skin.widgets.forEachIndexed { wi, w ->
            if (!w.show) return@forEachIndexed
            // 出现动画：进场时 0→1，按序 stagger
            val appear by animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = animSpec(w.anim.copy(delayMs = w.anim.delayMs + wi * 45)),
                label = "skinAppear_${w.id}_$wi",
            )

            // 尺寸：优先 w/h，其次 frac×S，最后 id 默认
            val width: Dp = w.w?.dp ?: w.frac?.let { S * it } ?: (S * 0.12f)
            val height: Dp = w.h?.dp ?: w.frac?.let { S * it } ?: width
            val xPx = (S * w.x - width / 2f)
            val yPx = (S * w.y - height / 2f)

            // 动画变换
            val animType = w.anim.type
            val animAlpha = when (animType) {
                "fade", "slideup", "slidedown", "slideleft", "slideright", "blur" -> appear
                else -> appear
            }
            val animScale = when (animType) {
                "scale", "pop" -> w.anim.fromScale + (1f - w.anim.fromScale) * appear
                else -> 1f
            }
            var slideX = 0f; var slideY = 0f
            val distPx = with(density) { w.anim.distance.dp.toPx() }
            when (animType) {
                "slideup" -> slideY = (1f - appear) * distPx
                "slidedown" -> slideY = -(1f - appear) * distPx
                "slideleft" -> slideX = (1f - appear) * distPx
                "slideright" -> slideX = -(1f - appear) * distPx
            }
            val rot = if (animType == "flip") (1f - appear) * -90f else 0f

            val baseMod = Modifier
                .offset { IntOffset((xPx.toPx() + slideX).toInt(), (yPx.toPx() + slideY).toInt()) }
                .graphicsLayerS(
                    alpha = (animAlpha * w.opacity).coerceIn(0f, 1f),
                    scale = animScale,
                    rotationX = rot,
                )
                .rotate(w.rotate)

            when (w.id) {
                "cover" -> {
                    AsyncImage(
                        model = current?.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = baseMod
                            .size(width, height)
                            .then(shapeMod(w))
                            .background(w.bg?.let { Color(it) } ?: Color(0xFF1A1A22))
                            .then(
                                if (w.borderW > 0f) Modifier.border(
                                    w.borderW.dp,
                                    w.borderColor?.let { Color(it) } ?: Color.White,
                                    shapeObj(w),
                                ) else Modifier
                            ),
                    )
                }
                "title", "artist" -> {
                    Text(
                        text = if (w.id == "title") (current?.title ?: "未选择音乐") else (current?.artist ?: ""),
                        color = w.color?.let { Color(it) } ?: if (w.id == "title") Color.White else Color(0xFFA8A8BC),
                        fontSize = (w.fontSize ?: 16).sp,
                        fontWeight = if (w.bold) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        textAlign = when (w.align) {
                            "left" -> TextAlign.Start; "right" -> TextAlign.End; else -> TextAlign.Center
                        },
                        modifier = baseMod.size(width, height),
                    )
                }
                "progress" -> {
                    if (w.ringStyle == "wave") {
                        SkinWaveDial(
                            modifier = baseMod.size(width),
                            progress = animatedProgress,
                            accent = w.color?.let { Color(it) } ?: themeColor,
                            disc = w.disc?.let { Color(it) } ?: w.color?.let { Color(it) } ?: themeColor,
                            track = w.track?.let { Color(it) } ?: Color(0x66FFFFFF),
                            strokeRatio = w.stroke / 100f,
                            amp = w.waveAmp, lobes = w.waveLobes, gap = w.waveGap,
                            speedMs = w.waveSpeed, phase = phase, isPlaying = isPlaying,
                        )
                    } else {
                        androidx.wear.compose.material3.CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = baseMod.size(width),
                            startAngle = 300f, endAngle = 600f,
                            colors = androidx.wear.compose.material3.ProgressIndicatorDefaults.colors(
                                indicatorColor = w.color?.let { Color(it) } ?: themeColor,
                                trackColor = w.track?.let { Color(it) } ?: Color(0x22FFFFFF),
                            ),
                            strokeWidth = w.stroke.dp, gapSize = 0.dp,
                        )
                    }
                }
                "play" -> {
                    Box(
                        modifier = baseMod
                            .size(width, height)
                            .then(shapeMod(w))
                            .background(w.bg?.let { Color(it) } ?: Color(0x33FFFFFF))
                            .clickable(onClick = { vm.togglePlay() }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "播放/暂停",
                            tint = w.color?.let { Color(it) } ?: Color.White,
                            modifier = Modifier.size(height * 0.5f),
                        )
                    }
                }
                "prev", "next" -> {
                    val isPrev = w.id == "prev"
                    val icon = iconFor(w.effectiveIcon) ?: if (isPrev) Icons.Filled.SkipPrevious else Icons.Filled.SkipNext
                    Box(
                        modifier = baseMod
                            .size(width, height)
                            .then(shapeMod(w))
                            .background(w.bg?.let { Color(it) } ?: Color(0x33FFFFFF))
                            .then(Modifier.longPressSeek(vm, isPrev)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = w.id, tint = w.color?.let { Color(it) } ?: Color.White,
                            modifier = Modifier.size(height * 0.5f))
                    }
                }
                "volume", "mode", "library" -> {
                    val icon = iconFor(w.effectiveIcon) ?: when (w.id) {
                        "mode" -> Icons.Filled.Repeat; "library" -> Icons.Filled.LibraryMusic
                        else -> Icons.Filled.VolumeDown
                    }
                    val tint = when (w.id) {
                        "mode" -> when (playMode) {
                            PlayMode.SEQUENTIAL -> Color(0xFF7A7A8C); else -> w.color?.let { Color(it) } ?: themeColor
                        }
                        else -> w.color?.let { Color(it) } ?: Color.White
                    }
                    Box(
                        modifier = baseMod.size(width, height).clickable(onClick = {
                            when (w.id) {
                                "volume" -> onOpenVolume?.invoke() ?: Unit
                                "mode" -> vm.cyclePlayMode()
                                "library" -> Unit // 归类列表入口由外层默认页提供，皮肤里可用手势
                            }
                        }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = w.id, tint = tint, modifier = Modifier.size(height * 0.8f))
                    }
                }
            }
        }
    }
}

/** 长按切歌键连续 seek：350ms 后每 200ms 步进，松手停 */
@Composable
private fun Modifier.longPressSeek(vm: PlayerViewModel, isPrev: Boolean): Modifier {
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    var fired by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val step = if (isPrev) -3000L else 3000L
    LaunchedEffect(interactionSource, step) {
        interactionSource.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> {
                    fired = false; job?.cancel()
                    job = scope.launch {
                        delay(350); fired = true; vm.seekBy(step)
                        while (isActive) { delay(200); vm.seekBy(step) }
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> { job?.cancel(); job = null }
            }
        }
    }
    return this.clickable(
        interactionSource = interactionSource, indication = null,
        onClick = {
            if (!fired) { if (isPrev) vm.prev() else vm.next() }
        },
    )
}

private fun shapeObj(w: WidgetStyle) = when (w.shape) {
    "circle" -> CircleShape
    "rounded" -> RoundedCornerShape((w.radius * 100).toInt())
    "rect" -> RoundedCornerShape(0.dp)
    else -> RoundedCornerShape(0.dp)
}

private fun shapeMod(w: WidgetStyle): Modifier =
    if (w.shape == "none") Modifier else Modifier.clip(shapeObj(w))

private fun iconFor(name: String): ImageVector? = when (name) {
    "play" -> Icons.Filled.PlayArrow; "pause" -> Icons.Filled.Pause
    "prev" -> Icons.Filled.SkipPrevious; "next" -> Icons.Filled.SkipNext
    "volume" -> Icons.Filled.VolumeDown; "mode" -> Icons.Filled.Repeat
    "shuffle" -> Icons.Filled.Shuffle; "library" -> Icons.Filled.LibraryMusic
    else -> null
}

/** graphicsLayer 变换（alpha/缩放/3D翻转），避免与 rotate 修饰符冲突 */
@Composable
private fun Modifier.graphicsLayerS(alpha: Float, scale: Float, rotationX: Float): Modifier =
    this.graphicsLayer {
        this.alpha = alpha
        scaleX = scale; scaleY = scale
        this.rotationX = rotationX
        cameraDistance = 12f * density
    }

/** 单 Canvas 波浪进度盘（参数完全由皮肤驱动） */
@Composable
private fun SkinWaveDial(
    modifier: Modifier,
    progress: Float,
    accent: Color,
    disc: Color,
    track: Color,
    strokeRatio: Float,
    amp: Float,
    lobes: Int,
    gap: Float,
    speedMs: Int,
    phase: Float,
    isPlaying: Boolean,
) {
    // 波幅直接切换（v1.67 删除平滑动画）
    val ampF = if (isPlaying) 1f else 0f
    // 三角查表（低端设备优化）：和角公式把每帧三角函数从 ~2160 次降到 2 次。
    // lobes 由皮肤配置，作为 remember key —— 正常不变，仅换皮肤时重建一次。
    val sampleN = 120
    val twoPi = 2f * PI.toFloat()
    val halfPi = PI.toFloat() / 2f
    // [0]=cos t [1]=sin t [2]=cos(lobes·t) [3]=sin(lobes·t)
    val tbl = remember(lobes) {
        Array(4) { FloatArray(sampleN + 1) }.also { t ->
            for (i in 0..sampleN) {
                val a = i * twoPi / sampleN
                t[0][i] = cos(a); t[1][i] = sin(a)
                t[2][i] = cos(lobes * a); t[3][i] = sin(lobes * a)
            }
        }
    }
    val arcTbl = remember(lobes) {
        Array(4) { FloatArray(sampleN + 1) }.also { t ->
            for (i in 0..sampleN) {
                val a = -halfPi + i * twoPi / sampleN
                t[0][i] = cos(a); t[1][i] = sin(a)
                t[2][i] = cos(lobes * a); t[3][i] = sin(lobes * a)
            }
        }
    }
    Canvas(modifier) {
        val c = center
        val R = size.minDimension / 2f
        val rt = R * 0.825f
        val a = rt * amp * ampF
        val sw = R * (strokeRatio.coerceIn(0.02f, 0.3f))
        val gp = R * gap
        val off = sw / 2f + gp
        val kphi = lobes * phase * twoPi
        val cosPhi = cos(kphi); val sinPhi = sin(kphi)

        val discPath = Path()
        for (i in 0..sampleN) {
            val wr = tbl[2][i] * cosPhi + tbl[3][i] * sinPhi
            val r = rt + a * wr - off
            val x = c.x + r * tbl[0][i]; val y = c.y + r * tbl[1][i]
            if (i == 0) discPath.moveTo(x, y) else discPath.lineTo(x, y)
        }
        discPath.close(); drawPath(discPath, disc, style = Fill)

        val trackPath = Path()
        for (i in 0..sampleN) {
            val wr = tbl[2][i] * cosPhi + tbl[3][i] * sinPhi
            val r = rt + a * wr
            val x = c.x + r * tbl[0][i]; val y = c.y + r * tbl[1][i]
            if (i == 0) trackPath.moveTo(x, y) else trackPath.lineTo(x, y)
        }
        drawPath(trackPath, track, style = Stroke(sw, cap = StrokeCap.Round))

        val frac = progress.coerceIn(0f, 1f)
        if (frac > 0.002f) {
            val arc = Path(); val m = (sampleN * frac).toInt().coerceAtLeast(2)
            for (i in 0..m) {
                val wr = arcTbl[2][i] * cosPhi + arcTbl[3][i] * sinPhi
                val r = rt + a * wr
                val x = c.x + r * arcTbl[0][i]; val y = c.y + r * arcTbl[1][i]
                if (i == 0) arc.moveTo(x, y) else arc.lineTo(x, y)
            }
            drawPath(arc, accent, style = Stroke(sw, cap = StrokeCap.Round))
        }
    }
}
