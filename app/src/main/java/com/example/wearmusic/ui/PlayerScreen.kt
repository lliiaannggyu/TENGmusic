package com.example.wearmusic.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
 * 手表播放器页（纯圆形表盘，内容约束在安全圆内）：
 *  - 背景：当前专辑封面（铺满全屏 + 高斯模糊 + 暗色遮罩）——专辑封面即背景
 *  - 安全圆：所有 UI 都收在「屏幕短边内缩一圈边距」的正圆内，既不超出圆形、也不贴边
 *  - 边缘进度环：内缩在安全圆里，从 12 点起顺时针，已播=亮蓝 / 未播=极淡白
 *  - 顶部系统时间由 AppScaffold 的 TimeText 提供
 *  - 屏幕中段：主控三键 ⏮ ⏯ ⏭ + 进度时间（整体上移，落在安全圆内）
 *  - 下半部分：收藏 / 列表 / 模式（上移并留底边缓冲，不超出、不贴边）
 *  - 上滑屏幕（或点「选专辑」提示）唤起专辑选取（Cover Flow）；点专辑直接播放
 *  - 左滑屏幕：切换到歌词页
 *
 * 关键：所有尺寸都按「安全圆直径 d 的比例」计算并 clamp 上下限，整体落在安全圆内。
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

    // 上滑手势累积位移（向上为负），超过阈值即唤起专辑选取
    var swipeAcc by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

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
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { swipeAcc = 0f },
                    onDragEnd = {
                        val threshold = with(density) { 70.dp.toPx() }
                        if (swipeAcc <= -threshold) onOpenAlbumFlow()
                    },
                ) { _, dragAmount -> swipeAcc += dragAmount }
            },
    ) {
            // 1) 背景层：当前曲目专辑封面铺满全屏 + 模糊 + 暗化（专辑封面即背景）
        AsyncImage(
            model = current?.albumArtUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(blurModifier(16f)),
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

        // 2) 安全圆：在圆形表盘内再留一圈边距，所有 UI 都不超出、不贴边
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val w = maxWidth
            val h = maxHeight
            // 安全圆直径：屏幕短边再内缩 edge，作为所有内容的边界
            val edge: Dp = (minOf(w, h) * 0.07f).coerceIn(10.dp, 20.dp)
            val d: Dp = minOf(w, h) - edge * 2f
            // 大圆环直径：与 WearApp 表圈一致（屏幕短边 - 6dp，半径 = 短边/2 - 3dp）
            val bigRingDiam: Dp = minOf(w, h) - 6.dp

            // —— 等比尺寸改为按安全圆直径 d 计算（带 min/max 钳制）——
            val playSize: Dp = (d * 0.27f).coerceIn(56.dp, 84.dp)
            val sideSize: Dp = (playSize * 0.78f)
            val bIconSize: Dp = (d * 0.060f).coerceIn(20.dp, 30.dp)
            val bIconInner: Dp = (bIconSize * 0.72f)
            val titleSp = (d.value * 0.034f).coerceIn(12f, 16f).sp
            val artistSp = (d.value * 0.026f).coerceIn(10f, 13f).sp
            val timeSp = (d.value * 0.024f).coerceIn(9f, 12f).sp
            val horizPad: Dp = (d * 0.10f).coerceIn(10.dp, 36.dp)
            val topPad: Dp = (d * 0.05f).coerceIn(14.dp, 24.dp) // 给 12 点 TimeText 让位
            val smallGap: Dp = (d * 0.02f).coerceIn(4.dp, 10.dp)
            val ctrlGap: Dp = (d * 0.05f).coerceIn(10.dp, 26.dp)

            // 居中安全圆：承载进度环与内容
            Box(
                modifier = Modifier
                    .size(d)
                    .align(Alignment.Center),
            ) {
                // 进度环：与外层大圆环（表圈）等大，沿圆形表盘边缘绘制
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .size(bigRingDiam)
                        .align(Alignment.Center),
                    startAngle = -90f,
                    endAngle = 270f,
                    colors = ProgressIndicatorDefaults.colors(
                        indicatorColor = ACCENT,
                        trackColor = Color(0x22FFFFFF),
                    ),
                    strokeWidth = 3.dp,
                    gapSize = 0.dp,
                )

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 顶部留白：给 AppScaffold 的 TimeText（12 点）让位
                    Spacer(Modifier.size(topPad))

                    // 标题 / 歌手（轻量，靠上）
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
                        text = current?.artist ?: "上滑选专辑 · 左滑看歌词",
                        color = Color(0xFFA8A8BC),
                        fontSize = artistSp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )

                    // 弹性间距：把主控推到屏幕中段（并整体上移）
                    Spacer(Modifier.weight(0.9f))

                    // —— 屏幕中段：主控三键（切歌 + 暂停）——
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

                    // 弹性间距：让底部按钮落在安全圆下半区、不贴边
                    Spacer(Modifier.weight(1.0f))

                    // 下半部分按钮（整体上移，落入安全圆内）
                    // 移除「选专辑」按钮，剩余三键整体左移一点并居中（上滑屏幕仍可唤起选曲）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = horizPad, end = horizPad)
                            .offset { IntOffset((-10).dp.roundToPx(), 0) },
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
                    }

                    // 底部缓冲：保证按钮不超出、不贴圆形边
                    Spacer(Modifier.weight(0.45f))
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
