package com.example.wearmusic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.example.wearmusic.PlayerViewModel

private val ACCENT = Color(0xFF34C7F4)

/**
 * 歌词页：圆形屏下的高级切换动画。
 *
 * 动画设计（逐行独立过渡，切换时互不干扰）：
 * - 缩放：当前行弹性放大到 1.12，其余缩到 0.95（spring 中阻尼，带轻微回弹）
 * - 颜色：当前行渐变到强调色，其余渐变到灰色（animateColorAsState）
 * - 透明度：当前行 1.0，其余 0.45，营造景深
 * - 位移：当前行变化时平滑滚动居中，并配合 snapFlingBehavior 吸附
 * - 背景：强调色径向渐变，强化圆形表盘氛围
 * - 表冠：支持旋转表冠滚动歌词
 *
 * 居中约定：滚动列表里【只有歌词行】（索引与 lyric 索引严格 1:1），
 * 顶部的「▲ 选曲」入口是覆盖层、不计入列表，因此 animateScrollToItem(idx)
 * 能把当前歌词行精确滚到屏幕正中，避免「差一位」导致的不居中。
 *
 * @param onOpenAlbumFlow 顶部「选曲」入口：列表会吃掉垂直手势，这里补一个点击入口进入专辑流
 */
@Composable
fun LyricsScreen(vm: PlayerViewModel, onOpenAlbumFlow: () -> Unit = {}) {
    val lyrics by vm.lyrics.collectAsState()
    val idx by vm.lyricIndex.collectAsState()
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester()

    // 当前行变化 → 平滑滚动并居中（索引与歌词数组严格对应）
    LaunchedEffect(idx, lyrics.size) {
        if (lyrics.isNotEmpty() && idx in lyrics.indices) {
            listState.animateScrollToItem(idx)
        }
    }

    ScreenScaffold(scrollState = listState) { padding ->
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
                contentPadding = padding,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryScroll(listState, focusRequester),
                flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(state = listState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 首尾留白：保证第一/最后一行也能滚到圆形屏正中、不被边缘裁切
                item { Spacer(Modifier.height(28.dp)) }
                items(lyrics.size) { i ->
                    LyricLine(text = lyrics[i].text, active = i == idx)
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }

        // 顶部「选曲」入口：独立覆盖层，不计入滚动列表，避免与歌词行索引错位
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
}

/** 单行歌词：缩放 / 颜色 / 透明度 / 字号全部走动画过渡；满宽 + 居中保证水平居中 */
@Composable
private fun LyricLine(text: String, active: Boolean) {
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
            .padding(vertical = 5.dp, horizontal = 12.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    )
}
