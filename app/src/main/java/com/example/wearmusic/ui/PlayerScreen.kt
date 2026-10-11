package com.example.wearmusic.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.example.wearmusic.PlayMode
import com.example.wearmusic.PlayerViewModel
import com.example.wearmusic.next
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ACCENT = Color(0xFF34C7F4)
private val CTRL_BG = Color(0xFF2A2A35) // 上一首/下一首背景色：比画布稍亮，保证可见

/**
 * 手表播放器页（按表盘直径等比缩放，手机大圆/真手表小圆都填满）：
 *  - 背景：当前专辑封面 + 高斯模糊 + 暗色遮罩
 *  - 边缘整圈进度环：从 12 点起顺时针，已播=亮蓝 / 未播=极淡白
 *  - 顶部系统时间由 AppScaffold 的 TimeText 提供
 *  - 中央：圆形专辑封面 + 曲名 + 歌手
 *  - 主控三键：⏮ ⏯ ⏭
 *  - 底部一行小键：收藏 / 列表 / 模式 / 专辑
 *
 * 关键：所有尺寸都按「表盘宽度 w 的比例」计算并 clamp 上下限，
 * 这样在手机上被圆形外框放大成大圆时，内容同步放大填满，不再缩在中间一团。
 */
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
        label = "playProgress",
    )

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
                .then(blurModifier(28f)),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color(0xCC000000),
                        0.35f to Color(0x55000000),
                        0.70f to Color(0xAA000000),
                        1.00f to Color(0xEE000000),
                    )
                ),
        )

        // 2) 边缘整圈进度环（Wear 标志性元素）
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            startAngle = -90f,
            endAngle = 270f,
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = ACCENT,
                trackColor = Color(0x22FFFFFF),
            ),
            strokeWidth = 4.dp,
            gapSize = 0.dp,
        )

        // 3) 按表盘直径等比缩放的内容
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val w = maxWidth
            val h = maxHeight

            // —— 等比尺寸（按表盘宽 w 的比例，带 min/max 钳制）——
            val albumSize: Dp = (w * 0.40f).coerceIn(64.dp, 140.dp)
            val playSize: Dp = (w * 0.20f).coerceIn(44.dp, 72.dp)
            val sideSize: Dp = (playSize * 0.74f)
            val bIconSize: Dp = (w * 0.055f).coerceIn(20.dp, 30.dp)
            val bIconInner: Dp = (bIconSize * 0.72f)
            val titleSp = (w.value * 0.033f).coerceIn(12f, 16f).sp
            val artistSp = (w.value * 0.025f).coerceIn(10f, 13f).sp
            val timeSp = (w.value * 0.023f).coerceIn(9f, 12f).sp
            val horizPad: Dp = (w * 0.09f).coerceIn(10.dp, 40.dp)
            val topPad: Dp = (h * 0.04f).coerceIn(14.dp, 26.dp) // 给 TimeText 让位
            val smallGap: Dp = (h * 0.018f).coerceIn(4.dp, 12.dp)
            val ctrlGap: Dp = (w * 0.05f).coerceIn(10.dp, 28.dp)

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 顶部留白：给 AppScaffold 的 TimeText（12 点）让位
                Spacer(Modifier.size(topPad))

                // —— 主内容簇：贴顶排布，整体偏上 ——
                // 圆形专辑封面
                Box(
                    modifier = Modifier
                        .size(albumSize)
                        .clip(CircleShape)
                        .background(Color(0xFF101015))
                ) {
                    if (current?.albumArtUri != null) {
                        AsyncImage(
                            model = current.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = Color(0x5534C7F4),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(albumSize * 0.42f)
                        )
                    }
                }

                Spacer(Modifier.size(smallGap))

                Text(
                    text = current?.title ?: "未选择音乐",
                    color = Color.White,
                    fontSize = titleSp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(smallGap * 0.4f))
                Text(
                    text = current?.artist ?: "轻触底部选曲",
                    color = Color(0xFFA8A8BC),
                    fontSize = artistSp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.size(smallGap * 1.6f))

                // 主控三键
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ctrlGap),
                ) {
                    PrevNextButton(
                        isPrev = true,
                        size = sideSize,
                        onClickOnce = { vm.prev() },
                        onSeekDelta = vm::seekBy,
                    )
                    PlayPauseButton(
                        isPlaying = isPlaying,
                        onToggle = vm::togglePlay,
                        size = playSize,
                    )
                    PrevNextButton(
                        isPrev = false,
                        size = sideSize,
                        onClickOnce = { vm.next() },
                        onSeekDelta = vm::seekBy,
                    )
                }

                Spacer(Modifier.size(smallGap))

                Text(
                    text = modeHint ?: "${formatMs(position)} / ${formatMs(duration)}",
                    color = if (modeHint != null) ACCENT else Color(0xFF9090A4),
                    fontSize = timeSp,
                    fontWeight = if (modeHint != null) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )

                // 弹性间距：把底栏推到表盘底部，主簇整体偏上
                Spacer(Modifier.weight(1f))

                // 底栏：收藏 / 列表 / 模式 / 专辑（永远在表盘底部安全区）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = (topPad * 0.6f), start = horizPad, end = horizPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    SmallIconAction(
                        icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        tint = if (isFavorite) Color(0xFFFF5A7A) else Color(0xFFD5D5E0),
                        size = bIconSize,
                        inner = bIconInner,
                        onClick = { if (current != null) vm.toggleFavorite(current.id) },
                    )
                    Spacer(Modifier.width(ctrlGap))
                    SmallIconAction(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        tint = Color(0xFFD5D5E0),
                        size = bIconSize,
                        inner = bIconInner,
                        onClick = onOpenTrackList,
                    )
                    Spacer(Modifier.width(ctrlGap))
                    SmallIconAction(
                        icon = when (playMode) {
                            PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                            PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                            PlayMode.SEQUENTIAL -> Icons.Filled.Repeat
                        },
                        tint = if (playMode == PlayMode.SEQUENTIAL) Color(0xFF7A7A8C) else ACCENT,
                        size = bIconSize,
                        inner = bIconInner,
                        onClick = {
                            val nextLabel = playMode.next().label
                            vm.cyclePlayMode()
                            modeHint = nextLabel
                        },
                    )
                    Spacer(Modifier.width(ctrlGap))
                    SmallIconAction(
                        icon = Icons.Filled.Album,
                        tint = Color(0xFFD5D5E0),
                        size = bIconSize,
                        inner = bIconInner,
                        onClick = onOpenAlbumFlow,
                    )
                }
            }
        }
    }
}

/** 播放/暂停主按钮（蓝色填充圆形） */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, onToggle: () -> Unit, size: Dp = 56.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(ACCENT)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = Color.White,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/** 上一首 / 下一首：单击切歌，长按连续快进/快退（背景色提亮保证可见） */
@Composable
private fun PrevNextButton(
    isPrev: Boolean,
    size: Dp = 44.dp,
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
            .size(size)
            .clip(CircleShape)
            .background(CTRL_BG)
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
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** 底栏小图标键 */
@Composable
private fun SmallIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    size: Dp,
    inner: Dp,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(inner),
        )
    }
}

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
