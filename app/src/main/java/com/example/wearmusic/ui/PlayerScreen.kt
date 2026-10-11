package com.example.wearmusic.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ProgressIndicatorDefaults
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.example.wearmusic.PlayMode
import com.example.wearmusic.PlayerViewModel
import com.example.wearmusic.next
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ACCENT = Color(0xFF34C7F4)
private val BG_VIGNETTE = listOf(Color(0x00000000), Color(0x99000000), Color(0xEE000000))

/**
 * 手表播放器页（圆形屏，仿图中的 iPod 风格）：
 * - 背景：当前播放曲目的专辑封面 + RenderEffect 高斯模糊（API 31+，低版降级为深色 vignette）
 * - 顶部：当前时间
 * - 弧形进度条沿表圈,顶部留出时钟位置
 * - 中部：圆形大封面 / 标题 / 艺术家
 * - 底部主控制：上一首 ⏮ / 播放-暂停 ▶ / 下一首 ⏭
 * - 底部辅助：播放模式（单曲循环/列表随机/列表顺序）/ 收藏 / 列表选曲
 * - 长按切歌键 = 连续 seek（长按上一首=快退 3s/次; 长按下一首=快进 3s/次,每 200ms 重复一次）
 * - 表冠：旋转调音量
 *
 * @param onOpenTrackList 唤起「列表式选曲」覆盖层
 * @param onOpenAlbumFlow 上滑进入 Cover Flow 专辑流（由外层 VerticalPager 提供）
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WearPlayerScreen(
    vm: PlayerViewModel,
    onOpenTrackList: () -> Unit,
    onOpenAlbumFlow: () -> Unit = {},
) {
    val tracks by vm.tracks.collectAsState()
    val index by vm.currentIndex.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val position by vm.position.collectAsState()
    val duration by vm.duration.collectAsState()
    val volume by vm.volume.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val playMode by vm.playMode.collectAsState()
    val current = tracks.getOrNull(index)
    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "playProgress"
    )

    // 顶部时钟
    var now by remember { mutableStateOf(currentTimeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = currentTimeText()
            delay(15_000)
        }
    }

    // 专辑缓慢旋转（黑胶感），暂停时冻结
    val transition = rememberInfiniteTransition(label = "vinylRotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing)
        ),
        label = "vinylAngle"
    )
    var frozenAngle by remember { mutableStateOf(0f) }
    LaunchedEffect(isPlaying, rotation) {
        if (isPlaying) frozenAngle = rotation
    }
    val coverAngle = if (isPlaying) rotation else frozenAngle

    // 表冠调音量
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Throwable) {}
    }

    val isFavorite = current != null && favorites.contains(current.id)

    // 切换播放模式后，底部时间行短暂变成模式名（1.5s 后恢复）
    var modeHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(modeHint) {
        if (modeHint != null) {
            delay(1500)
            modeHint = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1A1F), Color(0xFF0A0A0C))
                )
            )
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                vm.setVolume(volume + event.verticalScrollPixels * 0.0015f)
                true
            }
    ) {
        // =========== 背景层：当前曲目的专辑封面 + 模糊/暗化 ===========
        AsyncImage(
            model = current?.albumArtUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(blurModifier(28f)),
            contentScale = ContentScale.Crop
        )
        // 全屏暗色 vignette：让前景元素读得清
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = BG_VIGNETTE))
        )

        // =========== 弧形进度条：绕一圈,顶部留出时钟位置 ===========
        CircularProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier.fillMaxSize(),
            startAngle = 300f,
            endAngle = 240f,
            indicatorColor = ACCENT,
            trackColor = Color(0x33FFFFFF),
            strokeWidth = ProgressIndicatorDefaults.StrokeWidth
        )

        // =========== 顶部时间 ===========
        Text(
            text = now,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
        )

        // =========== 中部内容：圆形封面 + 标题 + 主控制 + 辅助 ===========
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // 圆形大封面 + 黑胶感
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(Color(0x55000000))
                    .graphicsLayer { rotationZ = coverAngle },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color(0x5534C7F4),
                    modifier = Modifier.size(34.dp)
                )
                AsyncImage(
                    model = current?.albumArtUri,
                    contentDescription = "专辑封面",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                // 黑胶中心孔
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0B0B0B))
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = current?.title ?: "未选择音乐",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                text = current?.artist ?: "在专辑流中选择",
                color = Color(0xFFB8B8C8),
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp)
            )

            Spacer(Modifier.height(10.dp))

            // =========== 主控制：上一首 / 播放 / 下一首(长按可调进度) ===========
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LongPressSeekButton(
                    isPrev = true,
                    onClickOnce = { vm.prev() },
                    onSeekDelta = { delta -> vm.seekBy(delta) }
                )
                CompactPlayPause(isPlaying = isPlaying, onToggle = vm::togglePlay)
                LongPressSeekButton(
                    isPrev = false,
                    onClickOnce = { vm.next() },
                    onSeekDelta = { delta -> vm.seekBy(delta) }
                )
            }

            Spacer(Modifier.height(8.dp))

            // =========== 辅助行：播放模式 / 收藏 / 列表选曲 ===========
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                SmallIconAction(
                    icon = when (playMode) {
                        PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                        PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                        PlayMode.SEQUENTIAL -> Icons.Filled.Repeat
                    },
                    tint = if (playMode == PlayMode.SEQUENTIAL) Color(0xFFE5E5EE) else ACCENT,
                    onClick = {
                        vm.cyclePlayMode()
                        modeHint = playMode.next().label
                    }
                )
                SmallIconAction(
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    tint = if (isFavorite) Color(0xFFFF5A7A) else Color(0xFFE5E5EE),
                    onClick = { if (current != null) vm.toggleFavorite(current.id) }
                )
                SmallIconAction(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    tint = Color(0xFFE5E5EE),
                    onClick = onOpenTrackList
                )
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = modeHint ?: "${formatMs(position)} / ${formatMs(duration)}",
                color = if (modeHint != null) ACCENT else Color(0xFF9C9CAE),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = "▲ 上滑选曲",
                color = Color(0x66FFFFFF),
                fontSize = 7.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable(onClick = onOpenAlbumFlow)
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * 长按切歌键:
 * - 单击 = 上一首/下一首
 * - 长按 = 进入连续 seek 模式,每 200ms seek 一次
 *
 * 用 [MutableInteractionSource] 监听按下/抬起,按下立刻先一次明显 seek,后进入循环。
 */
@Composable
private fun LongPressSeekButton(
    isPrev: Boolean,
    onClickOnce: () -> Unit,
    onSeekDelta: (Long) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(pressed) {
        if (pressed) {
            // 进入长按态:先来一次明显的 seek,接着每 200ms 再来一次
            val step = if (isPrev) -3000L else 3000L
            onSeekDelta(step)
            repeatJob?.cancel()
            repeatJob = scope.launch {
                // 一点点启动延迟,避免误操作
                delay(120)
                while (true) {
                    onSeekDelta(if (isPrev) -3000L else 3000L)
                    delay(200)
                }
            }
        } else {
            repeatJob?.cancel()
            repeatJob = null
        }
    }

    CompactButton(
        onClick = onClickOnce,
        modifier = Modifier.size(36.dp),
        interactionSource = interaction,
    ) {
        Icon(
            imageVector = if (isPrev) Icons.Filled.SkipPrevious else Icons.Filled.SkipNext,
            contentDescription = if (isPrev) "上一首（长按快退）" else "下一首（长按快进）",
            modifier = Modifier.size(20.dp),
            tint = Color.White
        )
    }
}

/** 播放/暂停按钮(居中主控),独立处理避免长按误触 */
@Composable
private fun CompactPlayPause(isPlaying: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E1E24))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = ACCENT,
            modifier = Modifier.size(26.dp)
        )
    }
}

/** 底部的「播放模式 / 收藏 / 列表选曲」图标按钮 */
@Composable
private fun SmallIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(22.dp)
            .clickable(onClick = onClick)
    )
}

/** 当前时间字符串(HH:mm) */
private fun currentTimeText(now: Date = Date()): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)

/** 像素格式化为 m:ss */
private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${"%02d".format(s % 60)}"
}

/**
 * 提供 RenderEffect 模糊修饰符（仅 API 31+ 生效,API < 31 走空 Modifier）。
 * 调用方需保证元素宿主支持 RenderEffect(在 graphicsLayer 内)。
 */
@Composable
private fun blurModifier(radiusDp: Float): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Modifier
    val density = LocalDensity.current
    val radiusPx = with(density) { radiusDp.dp.toPx() }
    return Modifier.graphicsLayer {
        renderEffect = RenderEffect
            .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
}
