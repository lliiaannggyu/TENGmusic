package com.tengwear.tengmusic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import kotlin.math.abs
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Text
import com.tengwear.tengmusic.PlayerViewModel

/**
 * 歌词页：圆形屏下的高级切换动画 + 表冠滚动 + 点击续播。
 *
 * 居中约定：
 * - 直接用 Box(contentAlignment = Center) 包裹 ScalingLazyColumn，不再套 ScreenScaffold
 *   （ScreenScaffold 的 contentPadding 会在圆屏上把内容整体往下压，导致「歌词偏下」）。
 * - 列表只放歌词行（索引与歌词严格 1:1），animateScrollToItem(idx) 即可把当前行精确滚到正中。
 *
 * 交互：
 * - 表冠（实体 / 三星虚拟表冠）滚动歌词（官方 rotaryScrollable）
 * - 手指上下滑动同样可翻动歌词
 * - 点击某一行 → 从该句歌词的时间戳继续播放
 * - 专辑流（Cover Flow）由播放页上滑唤起
 */
@Composable
fun LyricsScreen(
    vm: PlayerViewModel,
    onOpenAlbumFlow: () -> Unit = {},
    active: Boolean = true,
) {
    val lyrics by vm.lyrics.collectAsState()
    val idx by vm.lyricIndex.collectAsState()
    val position by vm.position.collectAsState()
    // 歌词着色：跟随专辑主色或用户自定义色（默认白，专辑无封面时）
    val lyricColor by vm.lyricColor.collectAsState()
    // 歌词切换动画（设置里可选，带实时预览）
    val anim by vm.lyricTransition.collectAsState()
    // 初始化即把当前句作为中心项，避免首次进入先停在顶部再「跳」到中间的闪动
    val listState = rememberScalingLazyListState(initialCenterItemIndex = idx.coerceAtLeast(0))
    val focusRequester = rememberRotaryFocusRequester(active)

    // 居中逻辑（消除「快速上跳一下」与逐句卡顿）：
    //  - 进入歌词页 / 切歌 / 点歌词 seek（大幅跳变）→ 瞬时 scrollToItem，绝不带动画上跳；
    //  - 同一份歌词内逐句推进（±1）→ 平滑动画跟随当前句；
    //  - 仅在 active（本页可见）时定位，隐藏态不空转。
    var prevIdx by remember { mutableStateOf<Int?>(null) }
    var prevList by remember { mutableStateOf<Any?>(null) }
    var prevActive by remember { mutableStateOf(false) }
    LaunchedEffect(lyrics, idx, active) {
        val wasActive = prevActive
        prevActive = active
        if (!active || lyrics.isEmpty() || idx !in lyrics.indices) return@LaunchedEffect
        val newSet = prevList !== lyrics
        val enter = !wasActive && active
        val bigJump = prevIdx == null || prevIdx !in lyrics.indices ||
            abs(idx - (prevIdx ?: idx)) > 1
        val needInstant = newSet || enter || bigJump
        if (needInstant) listState.scrollToItem(idx) else listState.animateScrollToItem(idx)
        prevIdx = idx
        prevList = lyrics
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0x1F34C7F4), Color(0x00000000))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (lyrics.isEmpty()) {
            Text(
                "暂无歌词\n在文件管理页匹配同名 .lrc",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                fontSize = 12.sp
            )
        } else {
            ScalingLazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .let { m -> if (active) m.rotaryScroll(listState, focusRequester) else m },
                flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(state = listState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 注意：列表里只有歌词行（无首尾 spacer），故列表下标 == 歌词下标，
                // animateScrollToItem(idx) 才能把当前行精确滚到圆形屏正中（之前多出的 spacer
                // 让下标错开一位，导致整页歌词都偏下）。
                items(lyrics.size) { i ->
                    val line = lyrics[i]
                    val next = lyrics.getOrNull(i + 1)
                    // 句内播放进度（供逐字填充 / 逐字显示动画使用）
                    val lineDur = ((next?.timeMs ?: (line.timeMs + 4000L)) - line.timeMs)
                        .coerceAtLeast(1L)
                    val lineProgress =
                        ((position - line.timeMs).toFloat() / lineDur).coerceIn(0f, 1f)
                    LyricLine(
                        text = line.text,
                        active = i == idx,
                        activeColor = Color(lyricColor),
                        anim = anim,
                        lineProgress = if (i == idx) lineProgress else 0f,
                        onClick = { vm.seekToLyric(line.timeMs) },
                    )
                }
            }
        }
    }
}
