package com.example.wearmusic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Text
import com.example.wearmusic.PlayerViewModel

private val ACCENT = Color(0xFF34C7F4)

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
 * - 顶部「▲ 选曲」入口：覆盖层，点击进入专辑流
 */
@Composable
fun LyricsScreen(
    vm: PlayerViewModel,
    onOpenAlbumFlow: () -> Unit = {},
    active: Boolean = true,
) {
    val lyrics by vm.lyrics.collectAsState()
    val idx by vm.lyricIndex.collectAsState()
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester(active)

    // 当前行变化 → 平滑滚动并居中（索引与歌词数组严格对应，无偏移）
    LaunchedEffect(idx, lyrics.size) {
        if (lyrics.isNotEmpty() && idx in lyrics.indices) {
            listState.animateScrollToItem(idx)
        }
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
                    LyricLine(
                        text = lyrics[i].text,
                        active = i == idx,
                        onClick = { vm.seekToLyric(lyrics[i].timeMs) },
                    )
                }
            }
        }

        // 顶部「选曲」入口：独立覆盖层，不计入滚动列表
        Text(
            text = "▲ 选曲",
            color = Color(0x88FFFFFF),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
                .clickable(onClick = onOpenAlbumFlow)
        )
    }
}

/** 单行歌词：缩放 / 颜色 / 透明度 / 字号全部走动画过渡；满宽 + 居中保证水平居中；点击跳播 */
@Composable
private fun LyricLine(text: String, active: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.12f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "lyricScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0.45f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "lyricAlpha"
    )
    val color by animateColorAsState(
        targetValue = if (active) ACCENT else Color.Gray,
        label = "lyricColor"
    )
    val fontSize by animateFloatAsState(
        targetValue = if (active) 17f else 13f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "lyricFontSize"
    )

    Text(
        text = text,
        textAlign = TextAlign.Center,
        fontSize = fontSize.sp,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 12.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    )
}
