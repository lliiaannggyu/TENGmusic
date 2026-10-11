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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CircularProgressIndicator
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
private val ACCENT_DIM = Color(0xFF1E8AB0)

/**
 * 手表播放器页（圆形屏，仿 iPod 经典圆盘的极简播放器）：
 *  - 背景：当前曲目专辑封面 + RenderEffect 高斯模糊 + 顶部到底部的暗色遮罩
 *  - 顶部 12 点位置：当前时间（盖在进度环上方）
 *  - 弧形进度环：粗 7dp，从 12 点顺时针一圈；进度已播放=亮蓝，未播放=极淡白
 *  - 中部：大圆形封面（占表盘直径的 50%）+ 黑胶感慢转 + 中心轴孔
 *  - 封面下方：曲名（粗）+ 歌手（细灰）
 *  - 主控制三键（44 / 52 / 44 dp 圆形）⏮ ⏸ ⏭
 *  - 底部辅助三键（22 dp）：播放模式 / 收藏 / 列表选曲
 *  - 长按 ⏮/⏭ = 连续 seek 3s/次
 *  - 表冠 = 音量
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
        label = "playProgress",
    )

    var now by remember { mutableStateOf(currentTimeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = currentTimeText()
            delay(15_000)
        }
    }

    val transition = rememberInfiniteTransition(label = "vinylRotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing)
        ),
        label = "vinylAngle",
    )
    var frozenAngle by remember { mutableStateOf(0f) }
    LaunchedEffect(isPlaying, rotation) {
        if (isPlaying) frozenAngle = rotation
    }
    val coverAngle = if (isPlaying) rotation else frozenAngle

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Throwable) {}
    }

    val isFavorite = current != null && favorites.contains(current.id)

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
            .background(Color(0xFF06070A))
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                vm.setVolume((volume + event.verticalScrollPixels * 0.0015f).coerceIn(0f, 1f))
                true
            },
    ) {
        // 1) 背景层：当前曲目专辑封面 + 模糊 + 暗化
        AsyncImage(
            model = current?.albumArtUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(blurModifier(36f)),
            contentScale = ContentScale.Crop,
        )
        // 自上而下：顶部暗（突出时间）→ 中部略透（让封面透出一点颜色）→ 底部深（突出控制键）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color(0xCC000000),
                        0.35f to Color(0x66000000),
                        0.70f to Color(0xAA000000),
                        1.00f to Color(0xEE000000),
                    )
                ),
        )

        // 2) 弧形进度环：粗 7dp，完整绕一圈从 12 点起；顶部时间压在上面
        CircularProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier.fillMaxSize(),
            startAngle = -90f,
            endAngle = 270f,
            indicatorColor = ACCENT,
            trackColor = Color(0x22FFFFFF),
            strokeWidth = 7.dp,
        )

        // 3) 顶部时间（压进度环之上）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = now,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // 4) 中部内容：封面 + 标题 + 主控 + 辅助
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(4.dp))

            // 大圆形封面 —— 直径 140dp（在 396dp 表盘上约占 36%，视觉上明确是主体）
            CoverDisc(
                albumArtUri = current?.albumArtUri,
                rotationAngle = coverAngle,
                sizeDp = 140,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = current?.title ?: "未选择音乐",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = current?.artist ?: "上滑专辑流选曲",
                color = Color(0xFFA8A8BC),
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp),
            )

            Spacer(Modifier.height(14.dp))

            // 主控三键
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                LongPressSeekButton(
                    isPrev = true,
                    onClickOnce = { vm.prev() },
                    onSeekDelta = vm::seekBy,
                )
                PlayPauseButton(
                    isPlaying = isPlaying,
                    onToggle = vm::togglePlay,
                )
                LongPressSeekButton(
                    isPrev = false,
                    onClickOnce = { vm.next() },
                    onSeekDelta = vm::seekBy,
                )
            }

            Spacer(Modifier.height(14.dp))

            // 辅助三键
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                SmallIconAction(
                    icon = when (playMode) {
                        PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                        PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                        PlayMode.SEQUENTIAL -> Icons.Filled.Repeat
                    },
                    tint = if (playMode == PlayMode.SEQUENTIAL) Color(0xFF7A7A8C) else ACCENT,
                    onClick = {
                        val nextLabel = playMode.next().label
                        vm.cyclePlayMode()
                        modeHint = nextLabel
                    },
                )
                SmallIconAction(
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    tint = if (isFavorite) Color(0xFFFF5A7A) else Color(0xFFD5D5E0),
                    onClick = { if (current != null) vm.toggleFavorite(current.id) },
                )
                SmallIconAction(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    tint = Color(0xFFD5D5E0),
                    onClick = onOpenTrackList,
                )
            }

            Spacer(Modifier.height(8.dp))
            // 进度时间（切换播放模式时短暂被替换成模式名）
            Text(
                text = modeHint ?: "${formatMs(position)} / ${formatMs(duration)}",
                color = if (modeHint != null) ACCENT else Color(0xFF9090A4),
                fontSize = 10.sp,
                fontWeight = if (modeHint != null) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
        }

        // 5) 上滑提示（放在最底层，让用户知道怎么去选曲）
        Text(
            text = "▲ 上滑 · 专辑选曲",
            color = Color(0x55FFFFFF),
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .clickable(onClick = onOpenAlbumFlow),
        )
    }
}

/**
 * 黑胶感大封面：圆盘比例外圈（深灰）+ 缩进的圆盘内圈（黑）+ 封面图 + 中心轴孔。
 * 整体 [sizeDp] = 直径。封面比外圈略小，制造「唱片层」效果。
 */
@Composable
private fun CoverDisc(
    albumArtUri: Any?,
    rotationAngle: Float,
    sizeDp: Int,
) {
    val outer = sizeDp.dp
    val inner = (sizeDp - 6).dp
    val centerHole = (sizeDp / 8).dp

    Box(
        modifier = Modifier
            .size(outer)
            .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    0f to Color(0xFF2A2A32),
                    1f to Color(0xFF050507),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 内圈：黑胶盘面，旋转（但封面不旋转，避免文字颠倒；只让外面的环纹转）
        Box(
            modifier = Modifier
                .size(inner)
                .clip(CircleShape)
                .background(Color(0xFF0A0A0E))
                .graphicsLayer { rotationZ = rotationAngle },
        ) {
            // 封面图（在黑胶纹理之上，所以和盘一起转）
            AsyncImage(
                model = albumArtUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            // 黑胶纹：若封面图未加载，给点纹理提示
            if (albumArtUri == null) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color(0x5534C7F4),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size((sizeDp * 0.35).dp),
                )
            }
            // 中心轴孔（红点）
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(centerHole)
                    .clip(CircleShape)
                    .background(Color(0xFFE84545)),
            )
        }
    }
}

/**
 * 主控播放/暂停键 —— 最大的圆形按钮，蓝色填充。
 */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(ACCENT)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = Color.White,
            modifier = Modifier.size(30.dp),
        )
    }
}

/**
 * 长按切歌键：
 *  - 单击 = 上一首/下一首
 *  - 长按 = 进入连续 seek 模式（每 200ms seek ±3s）
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
            val step = if (isPrev) -3000L else 3000L
            onSeekDelta(step)
            repeatJob?.cancel()
            repeatJob = scope.launch {
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

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFF1B1B22))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClickOnce,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPrev) Icons.Filled.SkipPrevious else Icons.Filled.SkipNext,
            contentDescription = if (isPrev) "上一首（长按快退）" else "下一首（长按快进）",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 辅助行小图标按钮 */
@Composable
private fun SmallIconAction(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** HH:mm */
private fun currentTimeText(now: Date = Date()): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)

/** m:ss */
private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${"%02d".format(s % 60)}"
}

/** RenderEffect 模糊修饰符（仅 API 31+）。 */
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