package com.example.wearmusic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import androidx.wear.compose.material.Text
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
 */
@Composable
fun LyricsScreen(vm: PlayerViewModel) {
    val lyrics by vm.lyrics.collectAsState()
    val idx by vm.lyricIndex.collectAsState()
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester()

    // 当前行变化 → 平滑滚动并居中
    LaunchedEffect(idx) {
        if (idx in lyrics.indices) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryScroll(listState, focusRequester),
                flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(state = listState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item { Spacer(Modifier.height(40.dp)) }
                items(lyrics.size) { i ->
                    LyricLine(text = lyrics[i].text, active = i == idx)
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

/** 单行歌词：缩放 / 颜色 / 透明度 / 字号全部走动画过渡 */
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
            .padding(vertical = 5.dp, horizontal = 10.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    )
}
