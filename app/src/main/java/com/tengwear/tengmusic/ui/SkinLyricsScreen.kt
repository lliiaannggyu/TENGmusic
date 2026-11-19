package com.tengwear.tengmusic.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Text
import com.tengwear.tengmusic.LyricTextStyle
import com.tengwear.tengmusic.LyricsSkin
import com.tengwear.tengmusic.PlayerViewModel
import com.tengwear.tengmusic.SkinAnim
import kotlin.math.abs

/**
 * v2 声明式歌词皮肤渲染引擎。
 *
 * 每行歌词按 [LyricsSkin] 的 active/inactive 样式渲染：
 *  - 字体大小 / 粗细 / 颜色 / 不透明度 / 缩放 / 发光
 *  - 进入动画（成为当前行）：滑入方向 / 淡入 / 缩放 / 翻转 / 模糊，含时长与缓动
 *  - 退出动画（离开当前行）：同上，独立配置
 *  - 逐字填充 / 打字机效果（karaoke.fill / rest）
 *  - 整页对齐、行距、内边距、背景色 / 弱光
 */
@Composable
fun SkinLyricsScreen(
    vm: PlayerViewModel,
    skin: LyricsSkin,
    active: Boolean = true,
) {
    val lyrics by vm.lyrics.collectAsState()
    val idx by vm.lyricIndex.collectAsState()
    val position by vm.position.collectAsState()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = idx.coerceAtLeast(0))
    val focusRequester = rememberRotaryFocusRequester(active)

    var prevIdx by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(lyrics, idx, active) {
        if (!active || lyrics.isEmpty() || idx !in lyrics.indices) return@LaunchedEffect
        val big = prevIdx == null || prevIdx !in lyrics.indices || abs(idx - (prevIdx ?: idx)) > 1
        if (big) listState.scrollToItem(idx) else listState.animateScrollToItem(idx)
        prevIdx = idx
    }

    val bgBase = skin.bgColor?.let { Color(it) } ?: Color.Black
    val hoverAlign: Alignment.Horizontal = when (skin.align) {
        "left" -> Alignment.Start; "right" -> Alignment.End; else -> Alignment.CenterHorizontally
    }
    val textAlign = when (skin.align) {
        "left" -> TextAlign.Start; "right" -> TextAlign.End; else -> TextAlign.Center
    }

    val glowBrush = if (skin.bgGlow != null) {
        androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(
                Color(skin.bgGlow).copy(alpha = skin.bgGlowStrength),
                Color.Transparent,
            )
        )
    } else null
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBase)
            .then(if (glowBrush != null) Modifier.background(glowBrush) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (lyrics.isEmpty()) {
            Text("暂无歌词\n在文件管理页匹配同名 .lrc", textAlign = TextAlign.Center,
                color = Color.Gray, fontSize = 12.sp)
        } else {
            ScalingLazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = skin.padDp.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxSize()
                    .let { if (active) mRotaryScroll(it, listState, focusRequester) else it },
                flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(state = listState),
                horizontalAlignment = hoverAlign,
            ) {
                items(lyrics.size) { i ->
                    val line = lyrics[i]
                    val next = lyrics.getOrNull(i + 1)
                    val lineDur = ((next?.timeMs ?: (line.timeMs + 4000L)) - line.timeMs).coerceAtLeast(1L)
                    val lineProgress = ((position - line.timeMs).toFloat() / lineDur).coerceIn(0f, 1f)
                    SkinLyricLine(
                        text = line.text,
                        active = i == idx,
                        lineProgress = if (i == idx) lineProgress else 0f,
                        skin = skin,
                        textAlign = textAlign,
                        onClick = { vm.seekToLyric(line.timeMs) },
                    )
                }
            }
        }
    }
}

/** 规避 rotaryScroll 扩展的重名：统一走一个本地包装 */
@Composable
private fun mRotaryScroll(
    m: Modifier,
    state: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    fr: androidx.compose.ui.focus.FocusRequester,
): Modifier = m.rotaryScroll(state, fr)

@Composable
private fun SkinLyricLine(
    text: String,
    active: Boolean,
    lineProgress: Float,
    skin: LyricsSkin,
    textAlign: TextAlign,
    onClick: () -> Unit,
) {
    // 进入/退出动画各自独立；成为当前行用 enter，离开用 exit
    val spec: AnimationSpec<Float> = lyricSpec(if (active) skin.enter else skin.exit)
    val t by animateFloatAsState(targetValue = if (active) 1f else 0f, animationSpec = spec, label = "skinLyricT")

    val style: LyricTextStyle = if (active) skin.active else skin.inactive
    val enterType = skin.enter.type
    val exitType = skin.exit.type
    val animType = if (active) enterType else exitType
    val distPx = if (active) skin.enter.distance else skin.exit.distance

    // 位移
    var tx = 0f; var ty = 0f
    when (animType) {
        "slideup" -> ty = (1f - t) * distPx
        "slidedown" -> ty = -(1f - t) * distPx
        "slideleft" -> tx = (1f - t) * distPx
        "slideright" -> tx = -(1f - t) * distPx
    }
    // 缩放：scale 样式 × 动画 fromScale
    val animScale = when (animType) {
        "scale", "pop" -> (if (active) skin.enter.fromScale else skin.exit.fromScale).let {
            it + (1f - it) * t
        }
        else -> 1f
    }
    val scale = style.scale * animScale
    val alpha = (style.opacity) * (if (animType == "fade") t else 1f).coerceIn(0f, 1f)
    val rotX = if (animType == "flip") (1f - t) * -90f else 0f
    val blurPx = if (animType == "blur") (1f - t) * 8f else 0f

    val color = style.color?.let { Color(it) } ?: Color.White
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = (style.size * 2.6f + 12f).dp)
            .padding(vertical = (skin.lineGapDp / 2f).dp.coerceAtLeast(0.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val content: @Composable () -> Unit = {
            // 逐字效果（karaoke.fill / typewriter）
            val rendered: AnnotatedString? = when {
                active && enterType == "karaoke" -> {
                    val fill = skin.karaokeFill?.let { Color(it) } ?: color
                    val rest = skin.karaokeRest?.let { Color(it) } ?: fill.copy(alpha = 0.4f)
                    val shown = (lineProgress * text.length).toInt().coerceIn(0, text.length)
                    buildAnnotatedString {
                        append(text)
                        if (shown in 1 until text.length) {
                            addStyle(SpanStyle(color = fill), 0, shown)
                            addStyle(SpanStyle(color = rest), shown, text.length)
                        } else {
                            addStyle(SpanStyle(color = if (shown == 0) rest else fill), 0, text.length)
                        }
                    }
                }
                active && enterType == "typewriter" -> {
                    val shown = (lineProgress * text.length).toInt().coerceIn(0, text.length)
                    buildAnnotatedString { append(text.take(shown)) }
                }
                else -> null
            }
            Text(
                text = rendered ?: AnnotatedString(text),
                textAlign = textAlign,
                color = color,
                fontSize = style.size.sp,
                fontWeight = if (style.bold) androidx.compose.ui.text.font.FontWeight.Bold
                             else androidx.compose.ui.text.font.FontWeight.Normal,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().then(
                    if (style.glow) Modifier.graphicsLayer {
                        shadowElevation = 12f
                        this.alpha = alpha
                    } else Modifier.graphicsLayer { this.alpha = alpha }
                ),
            )
        }

        Box(
            modifier = Modifier.graphicsLayer {
                translationX = tx; translationY = ty
                scaleX = scale; scaleY = scale
                rotationX = rotX
                cameraDistance = 12f * density
                if (canBlur && blurPx > 0f) {
                    renderEffect = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                }
            },
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

private fun lyricSpec(a: SkinAnim): AnimationSpec<Float> = when (a.easing) {
    "linear" -> tween(a.durMs, a.delayMs, LinearEasing)
    "easein" -> tween(a.durMs, a.delayMs, FastOutLinearInEasing)
    "easeinout" -> tween(a.durMs, a.delayMs, FastOutSlowInEasing)
    "spring" -> spring(dampingRatio = 0.5f, stiffness = 300f)
    else -> tween(a.durMs, a.delayMs, LinearOutSlowInEasing)
}
