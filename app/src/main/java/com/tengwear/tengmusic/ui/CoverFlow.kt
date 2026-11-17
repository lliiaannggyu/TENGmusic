package com.tengwear.tengmusic.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.tengwear.tengmusic.Album
import com.tengwear.tengmusic.PlayerViewModel
import com.tengwear.tengmusic.coverflow.CoverFlow
import com.tengwear.tengmusic.coverflow.CoverFlowParams
import com.tengwear.tengmusic.coverflow.CoverFlowState
import com.tengwear.tengmusic.coverflow.rememberCoverFlowState
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * 专辑选曲页（iPod Classic「Cover Flow」，v1.47 起按专辑浏览）：
 * 一张封面 = 一张专辑（不再按单曲平铺）。
 *
 * 交互：
 *  - 表冠旋转 → 每咔哒一格翻一张专辑
 *  - 轻触侧面封面 → 滚到正中；轻触已居中的封面 → 打开该专辑的歌曲列表
 *  - 下滑屏幕 → 返回播放页
 *  - 打开动画：先定位到当前播放的专辑，两侧专辑从内向外依次展开（线性）
 *
 * 表冠焦点：项目统一 rememberRotaryFocusRequester(active)（详见历史注释）。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AlbumCoverFlow(
    vm: PlayerViewModel,
    modifier: Modifier = Modifier,
    sizeFraction: Float = 0.5f,
    onBack: () -> Unit = {},
    onOpenAlbum: (Album) -> Unit = {},
    active: Boolean = true,
) {
    val tracks by vm.tracks.collectAsState()
    val albums by vm.albums.collectAsState()
    val index by vm.currentIndex.collectAsState()
    val context = LocalContext.current

    // 最靠近屏幕中心的专辑下标（列表首尾各有一个占位 spacer，列表下标 - 1 = 专辑下标）
    val centeredIndex = remember { mutableStateOf(0) }
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val buzzMs by vm.flowBuzzMs.collectAsState()
    val buzzStrength by vm.flowBuzzStrength.collectAsState()
    val buzzMsHolder = remember { mutableIntStateOf(12) }
    buzzMsHolder.value = buzzMs
    val buzzAmpHolder = remember { mutableIntStateOf(255) }
    buzzAmpHolder.value = (255 * buzzStrength / 100).coerceIn(1, 255)
    val buzzSuppressUntil = remember { mutableStateOf(0L) }

    // 打开动画进度：0→1 线性；两侧专辑按 |i - startAlbum| 依次展开（从内向外）
    val openAnim = remember { Animatable(0f) }
    var startAlbumIdx by remember { mutableIntStateOf(0) }

    var state: CoverFlowState? = null
    state = rememberCoverFlowState(
        onItemClick = { idx ->
            val s = state
            if (s != null && idx == centeredIndex.value) {
                // 点「已居中」的专辑 → 打开它的歌曲列表
                albums.getOrNull(idx)?.let(onOpenAlbum)
            } else {
                s?.scrollToItem(idx)
            }
        }
    )

    val focusRequester = rememberRotaryFocusRequester(active)

    val density = LocalDensity.current
    val snapTolerancePx = with(density) { 2.dp.toPx() }

    LaunchedEffect(state) {
        val listState = state?.lazyListState ?: return@LaunchedEffect
        var stillFrames = 0
        while (true) {
            val newIdx = computeCenteredIndex(listState, albums.size)
            if (newIdx != centeredIndex.value) {
                centeredIndex.value = newIdx
                if (System.currentTimeMillis() >= buzzSuppressUntil.value) {
                    buzz(vibrator, buzzMsHolder.value, buzzAmpHolder.value)
                }
            }
            val offPx = centerOffsetPx(listState)
            if (!listState.isScrollInProgress && abs(offPx) > snapTolerancePx) {
                stillFrames++
                if (stillFrames >= 8) {
                    state?.scrollToItem(centeredIndex.value)
                    stillFrames = 0
                }
            } else {
                stillFrames = 0
            }
            delay(60)
        }
    }

    // 进入专辑流：先定位到「当前正在播放」的专辑，然后播放从内向外展开动画
    LaunchedEffect(active, albums.size) {
        if (!active || albums.isEmpty()) return@LaunchedEffect
        var tries = 0
        while (state?.geometry == null && tries < 100) { delay(16); tries++ }
        // 当前播放曲目所属专辑的下标（找不到 → 0）
        val cur = if (index in 0..tracks.lastIndex) {
            albums.indexOfFirst { index in it.trackIndices }.coerceAtLeast(0)
        } else 0
        startAlbumIdx = cur
        buzzSuppressUntil.value = System.currentTimeMillis() + 700
        state?.scrollToItem(cur)
        // 从内向外依次展开两边的专辑：动画值 = 已展开的「圈数」
        openAnim.snapTo(0f)
        openAnim.animateTo(1f, tween(durationMillis = 600, easing = LinearEasing))
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFF06070A)),
        contentAlignment = Alignment.Center
    ) {
        if (albums.isEmpty()) {
            Text(
                "暂无专辑\n授权后自动扫描本地音乐",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            return@Box
        }

        var dragDownAcc by remember { mutableFloatStateOf(0f) }

        val listState = state?.lazyListState

        val crownSens by vm.crownSens.collectAsState()
        val crownFling by vm.crownFling.collectAsState()
        val sensHolder = remember { mutableFloatStateOf(1f) }
        sensHolder.value = crownSens
        val flingHolder = remember { mutableFloatStateOf(1f) }
        flingHolder.value = crownFling
        val decay = rememberSplineBasedDecay<Float>()
        val scaledFling = remember(decay) {
            object : FlingBehavior {
                override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                    val v0 = initialVelocity * flingHolder.value
                    if (abs(v0) <= 50f) return v0
                    var lastValue = 0f
                    AnimationState(initialValue = 0f, initialVelocity = v0).animateDecay(decay) {
                        val delta = value - lastValue
                        lastValue = value
                        val consumed = scrollBy(delta)
                        if (abs(delta - consumed) > 0.5f) cancelAnimation()
                    }
                    return lastValue
                }
            }
        }
        val scaledListState = listState?.let { ls ->
            rememberScrollableState { delta -> ls.dispatchRawDelta(delta * sensHolder.value) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .let { m ->
                    if (scaledListState != null) {
                        m.rotaryScrollable(
                            RotaryScrollableDefaults.behavior(
                                scrollableState = scaledListState,
                                flingBehavior = scaledFling,
                                hapticFeedbackEnabled = false,
                            ),
                            focusRequester,
                        )
                    } else {
                        m
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragDownAcc = 0f },
                        onDragEnd = {
                            val threshold = with(density) { 60.dp.toPx() }
                            if (dragDownAcc >= threshold) onBack()
                        },
                    ) { _, dragAmount -> dragDownAcc += dragAmount }
                }
        ) {
            CoverFlow(
                state = state,
                modifier = Modifier.fillMaxSize(),
                params = CoverFlowParams(
                    size = sizeFraction,
                    offset = 0.4f,
                    angle = 55f,
                    shift = 0.4f,
                    zoom = 0.8f,
                    mirror = true,
                ),
            ) {
                items(albums.size) { i ->
                    // 从内向外展开：距起始专辑 dist 圈的专辑，在 openAnim 推进到 dist 时开始浮现
                    val dist = abs(i - startAlbumIdx).toFloat()
                    val appear = ((openAnim.value * 5.5f - dist).coerceIn(0f, 1f))
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = appear
                                val sc = 0.45f + 0.55f * appear
                                scaleX = sc
                                scaleY = sc
                            }
                    ) {
                        AlbumArtContent(album = albums[i])
                    }
                }
            }
        }

        // 底部居中：当前专辑名 / 歌手 / 曲目数
        val currentAlbum = albums.getOrNull(centeredIndex.value)
        val nameShadow = Shadow(color = Color.Black, offset = Offset(0f, 2f), blurRadius = 4f)
        val isNowPlaying = currentAlbum != null && index in 0..tracks.lastIndex &&
            index in currentAlbum.trackIndices

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp, start = 10.dp, end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentAlbum?.name ?: "",
                    fontSize = 14.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    style = TextStyle(shadow = nameShadow),
                )
                if (isNowPlaying) {
                    Spacer(Modifier.width(5.dp))
                    PlayingWave(color = Color(0xFF8FD8FF))
                }
            }
            Text(
                text = currentAlbum?.artist ?: "",
                fontSize = 10.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                color = Color.White,
                style = TextStyle(shadow = nameShadow),
            )
            Text(
                text = currentAlbum?.let { "${it.trackCount} 首 · 点按打开" } ?: "",
                fontSize = 9.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                color = Color(0xAAFFFFFF),
                style = TextStyle(shadow = nameShadow),
            )
        }
    }
}

/** 触发一次短促马达震动（时长/强度可在设置页自定义，默认 12ms / 默认强度） */
@Suppress("DEPRECATION")
private fun buzz(vibrator: Vibrator?, durationMs: Int, amplitude: Int) {
    if (vibrator == null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), amplitude))
    } else {
        vibrator.vibrate(durationMs.toLong())
    }
}

/** 根据可见项布局找出最靠近视口中心的项，换算成专辑下标（-1 = 首尾 spacer） */
private fun computeCenteredIndex(listState: LazyListState, count: Int): Int {
    if (count <= 0) return 0
    val layout = listState.layoutInfo
    val viewportCenter = layout.viewportSize.width / 2f
    var best = -1
    var bestDist = Float.MAX_VALUE
    for (item in layout.visibleItemsInfo) {
        val center = item.offset + item.size / 2f
        val d = abs(center - viewportCenter)
        if (d < bestDist) {
            bestDist = d
            best = item.index
        }
    }
    return if (best < 0) 0 else (best - 1).coerceIn(0, count - 1)
}

/** 当前最靠近视口中心的项相对视口中心的像素偏移（兜底归中判断用） */
private fun centerOffsetPx(listState: LazyListState): Float {
    val layout = listState.layoutInfo
    if (layout.visibleItemsInfo.isEmpty()) return 0f
    val viewportCenter = layout.viewportSize.width / 2f
    var bestDist = Float.MAX_VALUE
    var bestOff = 0f
    for (item in layout.visibleItemsInfo) {
        val center = item.offset + item.size / 2f
        val d = abs(center - viewportCenter)
        if (d < bestDist) {
            bestDist = d
            bestOff = center - viewportCenter
        }
    }
    return bestOff
}

/** 专辑封面内容：铺满方形封面 + 轻微边框（无封面显示音符占位） */
@Composable
private fun AlbumArtContent(album: Album) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF161616))
            .border(1.dp, Color(0x33FFFFFF))
    ) {
        Icon(
            imageVector = Icons.Filled.Album,
            contentDescription = null,
            tint = Color(0x55FFFFFF),
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.Center),
        )
        AsyncImage(
            model = album.albumArtUri,
            contentDescription = album.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

/** 「正在播放」声波标识：三根圆头声波柱，高度此消彼长地无限起伏 */
@Composable
private fun PlayingWave(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    val transition = rememberInfiniteTransition(label = "playing-wave")
    val h1 by transition.animateFloat(
        0.30f, 1.00f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "wave-bar-1",
    )
    val h2 by transition.animateFloat(
        1.00f, 0.35f,
        animationSpec = infiniteRepeatable(tween(460, easing = LinearEasing), RepeatMode.Reverse),
        label = "wave-bar-2",
    )
    val h3 by transition.animateFloat(
        0.45f, 0.95f,
        animationSpec = infiniteRepeatable(tween(340, easing = LinearEasing), RepeatMode.Reverse),
        label = "wave-bar-3",
    )
    Canvas(modifier = modifier.size(width = 15.dp, height = 11.dp)) {
        val barW = size.width * 0.20f
        val gap = (size.width - barW * 3f) / 2f
        val radius = CornerRadius(barW / 2f)
        listOf(h1, h2, h3).forEachIndexed { i, p ->
            val h = (size.height * p).coerceAtLeast(barW)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barW + gap), (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = radius,
            )
        }
    }
}
