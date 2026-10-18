package com.tengwear.tengmusic.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.tengwear.tengmusic.PlayerViewModel
import com.tengwear.tengmusic.Track
import com.tengwear.tengmusic.coverflow.CoverFlow
import com.tengwear.tengmusic.coverflow.CoverFlowParams
import com.tengwear.tengmusic.coverflow.CoverFlowState
import com.tengwear.tengmusic.coverflow.rememberCoverFlowState
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * 选曲页（iPod Classic「Cover Flow」）。
 *
 * 表冠交互说明：
 *  - 在 Wear OS 上，表冠（Rotary）事件只会派发给「拥有焦点」的节点，
 *    并且会沿着焦点树分发。
 *  - 本应用所有屏幕（播放器/歌词/设置/曲目列表/专辑/归类）统一用
 *    `rememberRotaryFocusRequester(active)`（见 Rotary.kt）：仅当页面/浮层
 *    `active` 时才 requestFocus 一次，保证「同一时刻只有一个节点持有表冠焦点」。
 *    三页横向翻页 + 专辑流叠加时，靠这个 active 开关互斥，谁也不抢谁。
 *  - 曾经误用 Wear 官方无参 `rememberActiveFocusRequester()`，它在
 *    AnimatedVisibility 叠加场景下无法把焦点放回本节点，导致专辑流表冠翻不动
 *    （其它页面此刻 active=false 已完全退焦，并非它们抢焦点）。已改回项目统一的
 *    `rememberRotaryFocusRequester(active)`，并在进入时持续确保焦点到位。
 *
 * 交互：轻触已居中的封面 → 直接播放该首歌（并关闭选曲）；轻触侧面封面 → 滚到正中。
 *      下滑屏幕 → 返回播放页。表冠旋转 → 每咔哒一格翻一张。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AlbumCoverFlow(
    vm: PlayerViewModel,
    modifier: Modifier = Modifier,
    sizeFraction: Float = 0.5f,
    onBack: () -> Unit = {},
    active: Boolean = true,
) {
    val tracks by vm.tracks.collectAsState()
    val index by vm.currentIndex.collectAsState()

    // 实时、可靠地算出「最靠近屏幕中心的歌曲」索引（替代库内部滞后的 selectedIndex）。
    // 列表首尾各有一个占位 spacer，所以 列表下标 - 1 = 歌曲下标。
    val centeredIndex = remember { mutableStateOf(0) }

    // 用可空 var 持有 state，避免在 rememberCoverFlowState 的回调里引用尚未赋值的局部 val
    var state: CoverFlowState? = null
    state = rememberCoverFlowState(
        onItemClick = { index ->
            val s = state
            // 点「已居中」的封面 → 直接播放该首歌并关闭选曲
            if (s != null && index == centeredIndex.value) {
                vm.selectTrack(index)
                onBack()
            } else {
                s?.scrollToItem(index)
            }
        }
    )

    // 表冠焦点：使用项目统一的 rememberRotaryFocusRequester(active)，与所有屏幕一致。
    // 当 active=true（专辑流显示）时抢占焦点；绝不混用 Wear 官方无参
    // rememberActiveFocusRequester（在 AnimatedVisibility 叠加场景下无法把焦点放回本节点）。
    val focusRequester = rememberRotaryFocusRequester(active)

    // 兜底归中容差：居中封面偏离视口中心超过 2dp 且静止持续多帧 → 补一次吸附
    val density = LocalDensity.current
    val snapTolerancePx = with(density) { 2.dp.toPx() }

    LaunchedEffect(state) {
        val listState = state?.lazyListState ?: return@LaunchedEffect
        // 轮询布局信息，实时算出最靠近中心的歌曲下标（无需 snapshotFlow，兼容性更好）
        var stillFrames = 0
        while (true) {
            centeredIndex.value = computeCenteredIndex(listState, tracks.size)
            // 兜底归中：表冠滚动用的是 RotaryScrollableDefaults.behavior（连续滚动、
            // 不带按项吸附），偶发会停在两张封面中间。滚动完全停止后若居中封面仍
            // 偏离视口中心且持续多帧，补一次吸附，杜绝"卡在切换动画那里不回正"。
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

    // 进入专辑流时，把封面滚动到「当前正在播放」的歌曲（而非默认停在第一张）。
    // index 是全局曲目下标（ViewModel 的 poll 已做队列映射，播过归类也不会错位）；
    // tracks.size 一并入键：打开专辑流时曲库若还在扫描，扫描完成后再补一次定位。
    LaunchedEffect(active, tracks.size) {
        if (!active || tracks.isEmpty()) return@LaunchedEffect
        // 等 Cover Flow 完成首次布局、几何参数就绪（否则 scrollToItem 算不出位移）
        var tries = 0
        while (state?.geometry == null && tries < 100) { delay(16); tries++ }
        val start = if (index in 0..tracks.lastIndex) index else 0
        state?.scrollToItem(start)
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFF06070A)),
        contentAlignment = Alignment.Center
    ) {
        if (tracks.isEmpty()) {
            Text(
                "暂无歌曲\n授权后自动扫描本地音乐",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            return@Box
        }

        // 下滑手势累积量：向下超过阈值即返回播放页
        var dragDownAcc by remember { mutableFloatStateOf(0f) }

        // 表冠：改用 Wear 官方 rotaryScrollable + RotaryScrollableDefaults.behavior ——
        // 它内部的 FocusTargetWithSemantics 会自动抢占表冠焦点（与项目其它屏幕同一机制，
        // 稳定可靠），彻底替代此前手写的 .focusable().onRotaryScrollEvent{}：
        // focusable() 不会自动抢焦点、手动 requestFocus 时机难对准，正是专辑流表冠
        // 时灵时不灵的真正根因。滚动由 CoverFlowState.lazyListState 驱动，
        // 松手后由 LazyRow 自带的吸附 fling 把封面归中。
        val listState = state?.lazyListState

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 官方表冠支持：自动抢占焦点 + 驱动封面滚动
                .let { m ->
                    if (listState != null) {
                        m.rotaryScrollable(
                            RotaryScrollableDefaults.behavior(scrollableState = listState),
                            focusRequester,
                        )
                    } else {
                        m
                    }
                }
                // 下滑返回播放页（指针事件与表冠完全独立，不会相互抢占）
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
                items(tracks) { track ->
                    TrackArtContent(track = track)
                }
            }
        }

        // 底部居中：当前歌曲名 / 歌手 / 专辑
        val currentTrack = tracks.getOrNull(centeredIndex.value)
        val nameShadow = Shadow(color = Color.Black, offset = Offset(0f, 2f), blurRadius = 4f)
        // 居中的封面正好是播放器当前曲目 → 标题旁挂「正在播放」声波标识
        val isNowPlaying = index in 0..tracks.lastIndex && centeredIndex.value == index

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp, start = 10.dp, end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentTrack?.title ?: "",
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
                text = currentTrack?.artist ?: "",
                fontSize = 10.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                color = Color.White,
                style = TextStyle(shadow = nameShadow),
            )
            Text(
                text = currentTrack?.album ?: "",
                fontSize = 10.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                color = Color.White,
                style = TextStyle(shadow = nameShadow),
            )
        }
    }
}

/**
 * 根据 LazyListState 的可见项布局，找出几何中心最靠近视口中心的项，
 * 再换算成歌曲下标（列表首尾各有一个占位 spacer，故 列表下标 - 1 = 歌曲下标）。
 */
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

/**
 * 当前最靠近视口中心的项，其中心相对视口中心的像素偏移。
 * 供 AlbumCoverFlow 的兜底归中判断使用：滚动完全停止后该偏移应归零，
 * 若持续非零说明停在了两张封面中间，需要补一次吸附。
 */
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

/** 单首歌曲封面内容：铺满方形封面 + 轻微边框 */
@Composable
private fun TrackArtContent(track: Track) {
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
            model = track.albumArtUri,
            contentDescription = track.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * 「正在播放」声波标识：三根圆头声波柱，高度此消彼长地无限起伏。
 * 挂在专辑流底部信息行的标题旁，标记居中的封面正是播放器当前曲目。
 * 纯 Canvas 绘制，不依赖扩展图标库。
 */
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
