package com.example.wearmusic.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ProgressIndicatorDefaults
import androidx.wear.compose.material.Text
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.wearmusic.PlayerViewModel

private val ACCENT = Color(0xFF34C7F4)

/**
 * 手表播放器页（圆形屏优先）：
 * - 贴表圈环形进度条 + 强调色径向渐变背景
 * - 专辑封面做「黑胶唱片」旋转动画：播放时匀速旋转，暂停时冻结在当前角度
 * - 旋转表冠调音量（手表原生交互）
 * - 播放模式 / 速度 / 收藏 / 三键控制
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(vm: PlayerViewModel, onOpenLyrics: () -> Unit = {}) {
    val tracks by vm.tracks.collectAsState()
    val index by vm.currentIndex.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val position by vm.position.collectAsState()
    val duration by vm.duration.collectAsState()
    val volume by vm.volume.collectAsState()
    val repeatMode by vm.repeatMode.collectAsState()
    val speed by vm.speed.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val current = tracks.getOrNull(index)
    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
    // 进度动画：使用 Wear 官方推荐的动画曲线，进度变化平滑不跳变
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "playProgress"
    )

    // 黑胶旋转：播放时匀速转，暂停冻结
    val transition = rememberInfiniteTransition(label = "vinylRotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing)
        ),
        label = "vinylAngle"
    )
    var frozenAngle by remember { mutableStateOf(0f) }
    LaunchedEffect(isPlaying, rotation) {
        if (isPlaying) frozenAngle = rotation
    }
    val angle = if (isPlaying) rotation else frozenAngle

    // 表冠调音量：顺时针增大
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (t: Throwable) {
            // ignore
        }
    }

    val isFavorite = current != null && favorites.contains(current.id)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0x1434C7F4), Color(0x00000000))
                )
            )
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                vm.setVolume(volume + event.verticalScrollPixels * 0.0015f)
                true
            },
        contentAlignment = Alignment.Center
    ) {
        // 贴合表圈的环形进度条（Wear 官方规范：顶部留 51° 间隙给时间/标题，
        // 描边宽度与进度动画均用 ProgressIndicatorDefaults 的推荐值）
        CircularProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier.fillMaxSize(),
            startAngle = 295.5f,
            endAngle = 245.5f,
            indicatorColor = ACCENT,
            trackColor = Color(0x22FFFFFF),
            strokeWidth = ProgressIndicatorDefaults.StrokeWidth
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.78f)
        ) {
            // 黑胶封面：旋转 + 中心孔；点击进入歌词页
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickable { onOpenLyrics() }
                    .graphicsLayer { rotationZ = angle },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = ACCENT,
                    modifier = Modifier.size(32.dp)
                )
                AsyncImage(
                    model = current?.albumArtUri,
                    contentDescription = "专辑封面",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                // 黑胶中心孔
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0B0B0B))
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(text = current?.title ?: "未选择音乐", fontSize = 13.sp, maxLines = 1)
            Text(
                text = current?.artist ?: "在「音乐库」页选择",
                fontSize = 10.sp,
                color = Color.Gray,
                maxLines = 1
            )

            Spacer(Modifier.height(6.dp))

            // 主控制：上一首 / 播放暂停 / 下一首
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompactButton(onClick = vm::prev) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首")
                }
                Button(onClick = vm::togglePlay, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放"
                    )
                }
                CompactButton(onClick = vm::next) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
                }
            }

            Spacer(Modifier.height(4.dp))

            // 辅助：播放模式 / 速度 / 收藏
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                CompactButton(onClick = vm::cycleRepeatMode) {
                    Text(
                        text = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> "单曲"
                            Player.REPEAT_MODE_ALL -> "循环"
                            else -> "顺序"
                        },
                        fontSize = 9.sp
                    )
                }
                CompactButton(onClick = { vm.setSpeed(nextSpeed(speed)) }) {
                    Text(text = "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x", fontSize = 9.sp)
                }
                CompactButton(
                    onClick = { if (current != null) vm.toggleFavorite(current.id) }
                ) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "取消收藏" else "收藏",
                        tint = if (isFavorite) Color(0xFFFF5A7A) else Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = "音量 ${(volume * 100).toInt()}% · ${formatMs(position)}/${formatMs(duration)}",
                fontSize = 9.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/** 播放速度循环档位 */
private fun nextSpeed(cur: Float): Float {
    val steps = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val next = steps.firstOrNull { it > cur + 0.01f }
    return next ?: steps.first()
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${"%02d".format(s % 60)}"
}
