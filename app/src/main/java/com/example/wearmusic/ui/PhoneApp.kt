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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.wearmusic.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ACCENT = Color(0xFF34C7F4)
private val BG_VIGNETTE = listOf(Color(0x00000000), Color(0x99000000), Color(0xEE000000))

/**
 * 手机端根界面：
 * 按需求"把下面的页面栏目去掉"，原本 5 页底部 NavigationBar 已删除。
 * 现只保留两个核心页：**播放器（首屏）+ Cover Flow 专辑流**，
 * 左右横滑切换，与图片中给出的"圆形 iPod 风"一致——只是容器从圆屏拓宽为矩形屏。
 */
@Composable
fun PhoneApp(vm: PlayerViewModel) {
    MaterialTheme {
        val pagerState = rememberPagerState(pageCount = { 2 })
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0F))) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> PhonePlayerScreen(vm = vm)
                    else -> AlbumCoverFlow(vm = vm, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * 手机端播放器页（Material3）：
 * - 背景：当前曲目的专辑封面 + RenderEffect 模糊 + 暗色 vignette
 * - 中部：圆形大封面 / 标题 / 歌手指针
 * - 控制行：上一首 / 播放 / 下一首,长按上一首/下一首 = 连续 seek
 * - 辅助行：耳机 / 收藏 / 喜欢
 * - 进度条：Material3 Slider,拖动定位
 */
@Composable
fun PhonePlayerScreen(vm: PlayerViewModel) {
    val tracks by vm.tracks.collectAsState()
    val index by vm.currentIndex.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val position by vm.position.collectAsState()
    val duration by vm.duration.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val current = tracks.getOrNull(index)
    val isFavorite = current != null && favorites.contains(current.id)

    // 进度条安全值
    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 220),
        label = "phoneProgress"
    )

    // 黑胶旋转
    val transition = rememberInfiniteTransition(label = "phoneVinylRotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(20_000, easing = LinearEasing)),
        label = "phoneVinylAngle"
    )
    var frozenAngle by remember { mutableStateOf(0f) }
    LaunchedEffect(isPlaying, rotation) {
        if (isPlaying) frozenAngle = rotation
    }
    val coverAngle = if (isPlaying) rotation else frozenAngle

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF1A1A1F), Color(0xFF08080A))))
    ) {
        // 模糊封面背景
        AsyncImage(
            model = current?.albumArtUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(blurPhoneModifier(40f)),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = BG_VIGNETTE))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // 圆形大封面
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(Color(0x55000000))
                    .graphicsLayer { rotationZ = coverAngle },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color(0x5534C7F4),
                    modifier = Modifier.size(80.dp)
                )
                AsyncImage(
                    model = current?.albumArtUri,
                    contentDescription = "专辑封面",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0B0B0B))
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = current?.title ?: "未选择音乐",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                text = current?.artist ?: "在专辑流中选择",
                color = Color(0xFFB0B0C0),
                fontSize = 14.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // 进度 Slider
            Slider(
                value = animatedProgress,
                onValueChange = { vm.seekToFraction(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = ACCENT,
                    activeTrackColor = ACCENT,
                    inactiveTrackColor = Color(0x33FFFFFF)
                )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMs(position), color = Color(0xFF9C9CAE), fontSize = 11.sp)
                Text(formatMs(duration), color = Color(0xFF9C9CAE), fontSize = 11.sp)
            }

            Spacer(Modifier.height(14.dp))

            // 主控制行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                PhoneLongPressSeekButton(
                    isPrev = true,
                    onClickOnce = { vm.prev() },
                    onSeekDelta = { vm.seekBy(it) }
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E24))
                        .clickable { vm.togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = ACCENT,
                        modifier = Modifier.size(34.dp)
                    )
                }
                PhoneLongPressSeekButton(
                    isPrev = false,
                    onClickOnce = { vm.next() },
                    onSeekDelta = { vm.seekBy(it) }
                )
            }

            Spacer(Modifier.height(18.dp))

            // 辅助行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Headphones,
                    contentDescription = null,
                    tint = Color(0xFFE5E5EE),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { /* 预留 */ }
                )
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) Color(0xFFFF5A7A) else Color(0xFFE5E5EE),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { if (current != null) vm.toggleFavorite(current.id) }
                )
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFFE5E5EE),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { /* 预留 */ }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * 手机端长按切歌键：
 * - 单击 = 上一首/下一首
 * - 长按 = 连续 seek（每 250ms 一次）
 */
@Composable
private fun PhoneLongPressSeekButton(
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
            onSeekDelta(if (isPrev) -3000L else 3000L)
            repeatJob?.cancel()
            repeatJob = scope.launch {
                delay(120)
                while (true) {
                    onSeekDelta(if (isPrev) -3000L else 3000L)
                    delay(250)
                }
            }
        } else {
            repeatJob?.cancel()
            repeatJob = null
        }
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color(0x1FFFFFFF))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClickOnce
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPrev) Icons.Filled.SkipPrevious else Icons.Filled.SkipNext,
            contentDescription = if (isPrev) "上一首（长按快退）" else "下一首（长按快进）",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * 提供 RenderEffect 模糊修饰符（仅 API 31+ 生效,API < 31 走空 Modifier）。
 */
@Composable
private fun blurPhoneModifier(radiusDp: Float): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Modifier
    val density = LocalDensity.current
    val radiusPx = with(density) { radiusDp.dp.toPx() }
    return Modifier.graphicsLayer {
        renderEffect = RenderEffect
            .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${"%02d".format(s % 60)}"
}
