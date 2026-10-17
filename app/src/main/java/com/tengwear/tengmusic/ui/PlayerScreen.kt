package com.tengwear.tengmusic.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.LibraryMusic
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
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.tengwear.tengmusic.PlayMode
import com.tengwear.tengmusic.PlayerViewModel
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ACCENT = Color(0xFF34C7F4)
private val CTRL_BG = Color(0xFF2A2A35)

/**
 * 手表播放器页（纯圆形表盘，内容约束在安全圆内）。
 *
 * 布局要点：
 *  - 承载 UI 的容器虽然是正方形（d × d），但可视边界是圆。圆形**顶部/底部两侧收窄**，
 *    因此顶部信息区与底部按钮区需要按 d 的比例加水平内边距，避免贴边、被圆边切掉。
 *  - 中间区域（控制按钮一带）是圆的最宽处，可以保持原样不加内边距。
 *  - 进度环例外：按**屏幕**短边计算直径并居中于屏幕中心。AppScaffold 顶部的
 *    TimeText（系统时间）槽位会把内容盒压矮一截，若按内容盒计算，圆环会偏小、
 *    圆心偏下，离屏幕边缘留出一段明显间隙。
 */
@Composable
fun WearPlayerScreen(
    vm: PlayerViewModel,
    onOpenTrackList: () -> Unit,
    onOpenAlbumFlow: () -> Unit = {},
    active: Boolean = true,
) {
    val tracks by vm.tracks.collectAsState()
    val index by vm.currentIndex.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val position by vm.position.collectAsState()
    val duration by vm.duration.collectAsState()
    val volume by vm.volume.collectAsState()
    val playMode by vm.playMode.collectAsState()
    val blurRadius by vm.blurRadius.collectAsState()
    val dimAlpha by vm.dimAlpha.collectAsState()
    val ringRadiusFrac by vm.ringRadiusFrac.collectAsState()
    val ringStroke by vm.ringStroke.collectAsState()
    val lyricColor by vm.lyricColor.collectAsState()
    val current = tracks.getOrNull(index)
    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "playProgress",
    )

    val focusRequester = rememberRotaryFocusRequester(active)

    var swipeAcc by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    var showVolume by remember { mutableStateOf(false) }
    var showCategoryList by remember { mutableStateOf(false) }

    // 返回键：浮层（归类列表 / 音量）打开时先关闭浮层，返回上一级而非退出应用
    BackHandler(enabled = active && (showCategoryList || showVolume)) {
        showCategoryList = false
        showVolume = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06070A))
            .let { m ->
                if (active) {
                    m.focusRequester(focusRequester)
                        .focusable()
                        .onRotaryScrollEvent { event ->
                            vm.setVolume((volume + event.verticalScrollPixels * 0.0015f).coerceIn(0f, 1f))
                            true
                        }
                } else {
                    m
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { swipeAcc = 0f },
                    onDragEnd = {
                        val threshold = with(density) { 70.dp.toPx() }
                        if (swipeAcc <= -threshold && !showVolume) onOpenAlbumFlow()
                    },
                ) { _, dragAmount -> swipeAcc += dragAmount }
            },
    ) {
        // 1) 背景层：封面铺满 + 模糊 + 渐变暗化
        AsyncImage(
            model = current?.albumArtUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(blurModifier(blurRadius)),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = (dimAlpha * 1.25f).coerceIn(0f, 1f)),
                        0.35f to Color.Black.copy(alpha = (dimAlpha * 0.55f).coerceIn(0f, 1f)),
                        0.70f to Color.Black.copy(alpha = (dimAlpha * 0.85f).coerceIn(0f, 1f)),
                        1.00f to Color.Black.copy(alpha = (dimAlpha * 1.25f).coerceIn(0f, 1f)),
                    )
                ),
        )

        // 2) 圆形安全区
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            val shortSide: Dp = minOf(w, h)
            val edge: Dp = (shortSide * 0.07f).coerceIn(10.dp, 20.dp)
            val d: Dp = shortSide - edge * 2f

            // 根容器 AppScaffold 顶部有 TimeText（系统时间）槽位，内容盒比真实屏幕
            // 矮一截。进度环要贴屏幕边缘，必须按**屏幕**短边计算直径；同时内容盒
            // 中心比屏幕中心低半个 TimeText 内缩量，圆心需向上补偿回去。
            val cfg = LocalConfiguration.current
            val screenShort: Dp = minOf(cfg.screenWidthDp.dp, cfg.screenHeightDp.dp)
            val topInset: Dp = (cfg.screenHeightDp.dp - h).coerceAtLeast(0.dp)
            val ringCenterOffsetY: Dp = -topInset / 2f

            val ringStrokeDp: Dp = ringStroke.dp
            // 按屏幕短边计算：50% 时直径 = 屏幕短边（顶到圆屏边缘，物理上限）
            val bigRingDiam: Dp = (screenShort * ringRadiusFrac * 2f)

            // —— 等比尺寸 ——
            val playSize: Dp = (d * 0.25f).coerceIn(52.dp, 76.dp)
            val sideSize: Dp = (playSize * 0.74f)
            val titleSp = (d.value * 0.034f).coerceIn(12f, 16f).sp
            val artistSp = (d.value * 0.026f).coerceIn(10f, 13f).sp
            val topPad: Dp = (d * 0.05f).coerceIn(14.dp, 24.dp)
            val smallGap: Dp = (d * 0.02f).coerceIn(4.dp, 10.dp)
            val ctrlGap: Dp = (d * 0.04f).coerceIn(8.dp, 18.dp)

            // 圆形顶部/底部收窄补偿：越靠近圆的上下极点，可用宽度越窄。
            // 这里按经验比例给上下区域留出水平内边距，让内容不再贴边。
            val topSidePad: Dp = d * 0.12f
            val bottomSidePad: Dp = d * 0.20f
            // 进度环 / 进度条跟随歌词主题色（而非固定 ACCENT）
            val themeColor = Color(lyricColor)

            // 主题（仅保留 M3E 一套 UI，经典主题已移除）
            // 按钮配色跟随歌词主题色 themeColor（专辑主色或用户自定义色）
            // 播放/暂停键与切歌键一致：主题色 + 半透明（按需求统一）
            val ctrlBg = themeColor.copy(alpha = 0.22f)
            val ctrlIcon = Color.White
            val playBg = themeColor.copy(alpha = 0.22f)
            val playIcon = Color.White
            val modeTint = if (playMode == PlayMode.SEQUENTIAL) Color(0xFF7A7A8C) else themeColor

            // 进度环：按屏幕短边计算直径，圆心补偿 TimeText 内缩后居中于屏幕中心，
            // 使其真正贴近圆屏边缘（文字/按钮仍按内容盒安全圆排布，避开 TimeText）。
            // 角度语义（Compose）：0°=3点钟，顺时针增大，270°=12点钟。
            // startAngle=300°（1点钟）顺时针扫到 endAngle=600°（等价 240°=11点钟），
            // 即 300°~600° 共 300° 的弧，顶部 240°~300°（11点~1点）留 60° 缺口，
            // 避开 AppScaffold 的系统时钟 TimeText，解决「时钟遮挡进度条」问题。
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .size(bigRingDiam)
                    .align(Alignment.Center)
                    .offset(y = ringCenterOffsetY),
                startAngle = 300f,
                endAngle = 600f,
                colors = ProgressIndicatorDefaults.colors(
                    indicatorColor = themeColor,
                    trackColor = Color(0x22FFFFFF),
                ),
                strokeWidth = ringStrokeDp,
                gapSize = 0.dp,
            )

            Box(
                modifier = Modifier
                    .size(d)
                    .align(Alignment.Center),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.size(topPad))

                    // ===== 顶部信息区（圆形上方窄区：加水平内边距）=====
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = topSidePad),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = current?.title ?: "未选择音乐",
                            color = Color.White,
                            fontSize = titleSp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures(onDoubleTap = { showCategoryList = true })
                                },
                        )
                        Spacer(Modifier.size(smallGap * 0.4f))
                        Text(
                            text = current?.artist ?: "",
                            color = Color(0xFFA8A8BC),
                            fontSize = artistSp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(smallGap * 0.5f))
                    }

                    // 弹性间距：把主控推到屏幕中段（略收紧，给底部留空间）
                    Spacer(Modifier.weight(0.85f))

                    // ===== 中间：主控三键（播放键=圆形，切歌键=横向胶囊）=====
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ctrlGap),
                    ) {
                        PrevNextButton(
                            isPrev = true,
                            size = sideSize,
                            pill = true,
                            onClickOnce = { vm.prev() },
                            onSeekDelta = vm::seekBy,
                            background = ctrlBg,
                            iconColor = ctrlIcon,
                        )
                        PlayPauseButton(
                            isPlaying = isPlaying,
                            onToggle = vm::togglePlay,
                            size = playSize,
                            pill = false,
                            background = playBg,
                            iconColor = playIcon,
                        )
                        PrevNextButton(
                            isPrev = false,
                            size = sideSize,
                            pill = true,
                            onClickOnce = { vm.next() },
                            onSeekDelta = vm::seekBy,
                            background = ctrlBg,
                            iconColor = ctrlIcon,
                        )
                    }

                    Spacer(Modifier.size(smallGap))

                    // 弹性间距：让底部按钮落在安全圆下半区
                    Spacer(Modifier.weight(0.95f))

                    // ===== 底部按钮区（圆形下方窄区：加水平内边距）=====
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = bottomSidePad),
                        contentAlignment = Alignment.Center,
                    ) {
                        TriPill(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = ctrlBg,
                            volumeIcon = when {
                                volume <= 0.001f -> Icons.Filled.VolumeOff
                                volume < 0.5f -> Icons.Filled.VolumeDown
                                else -> Icons.Filled.VolumeUp
                            },
                            modeIcon = when (playMode) {
                                PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                                PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                                PlayMode.SEQUENTIAL -> Icons.Filled.Repeat
                            },
                            modeTint = modeTint,
                            onVolume = { showVolume = !showVolume },
                            onMode = { vm.cyclePlayMode() },
                            onCategory = { showCategoryList = true },
                        )
                    }

                    // 底部缓冲（继续增大，把底部三按钮再往上移一档）
                    Spacer(Modifier.weight(0.80f))
                }
            }

            // 音量调节浮层：横向宽胶囊（M3E 风格圆角条）
            if (showVolume) {
                // 浮层自身接管表冠焦点：浮层根节点 focusable + onRotaryScrollEvent，
                // 打开时抢占焦点、关闭时归还播放器根节点，保证表冠全程可调音量。
                val volFr = remember { FocusRequester() }
                LaunchedEffect(showVolume) {
                    if (showVolume) {
                        try { volFr.requestFocus() } catch (_: Throwable) {}
                    } else {
                        try { focusRequester.requestFocus() } catch (_: Throwable) {}
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xAA000000))
                        .focusRequester(volFr)
                        .focusable()
                        .onRotaryScrollEvent { event ->
                            vm.setVolume(
                                (volume + event.verticalScrollPixels * 0.0015f).coerceIn(0f, 1f)
                            )
                            true
                        }
                        // 关浮层用不抢焦点的 tap 检测（clickable 自带 focusable 会抢焦点）
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { showVolume = false })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.84f)
                            .wrapContentHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "音量 ${(volume * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                        // 横胶囊：点按跳转 / 拖动跟随；表冠由播放器根节点统一处理
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x40FFFFFF))
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()
                                        var moved = false
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes
                                                .firstOrNull { it.id == down.id } ?: break
                                            if (change.changedToUp()) {
                                                if (!moved) {
                                                    // 轻点胶囊：直接跳到点击处
                                                    vm.setVolume(
                                                        (change.position.x / w).coerceIn(0f, 1f)
                                                    )
                                                }
                                                break
                                            }
                                            val dx = change.position.x - down.position.x
                                            if (!moved && abs(dx) > 6f) moved = true
                                            if (moved) {
                                                change.consume()
                                                // 一律按手指位置绝对值设置：
                                                // 避开 pointerInput(Unit) 块只执行一次、
                                                // 捕获的 volume 是陈旧值的问题
                                                vm.setVolume(
                                                    (change.position.x / w).coerceIn(0f, 1f)
                                                )
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(volume.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(themeColor, RoundedCornerShape(50)),
                            )
                            Text(
                                text = "${(volume * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
            }
        }

        // 归类列表浮层：双击歌名 / 点底部「归类」键唤起；点击类别播放该归类
        if (showCategoryList) {
            CategoryListScreen(vm = vm, onClose = { showCategoryList = false })
        }
    }
}

/** 播放/暂停主按钮：默认圆形；M3E 主题下改成长条形胶囊（pill） */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    size: Dp = 56.dp,
    background: Color = ACCENT,
    iconColor: Color = Color.White,
    pill: Boolean = false,
) {
    val shape = if (pill) RoundedCornerShape(percent = 50) else CircleShape
    val boxW = if (pill) size * 1.6f else size
    Box(
        modifier = Modifier
            .size(boxW, size)
            .clip(shape)
            .background(background)
            // 用 detectTapGestures 而非 clickable：clickable 自带 focusable，
            // 点击后会抢走播放器根节点的表冠焦点，导致表冠失灵
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggle() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = iconColor,
            modifier = Modifier.size(if (pill) size * 0.4f else size * 0.56f),
        )
    }
}

/** 上一首 / 下一首：短按切歌；长按连续快进/快退，松手即停且不再触发切歌 */
@Composable
private fun PrevNextButton(
    isPrev: Boolean,
    size: Dp = 44.dp,
    onClickOnce: () -> Unit,
    onSeekDelta: (Long) -> Unit,
    background: Color = CTRL_BG,
    iconColor: Color = Color.White,
    pill: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val shape = if (pill) RoundedCornerShape(percent = 50) else CircleShape
    // 胶囊态刻意拉宽（宽 > 高），形成横向长条胶囊；圆形态保持正方形
    val boxW = if (pill) size * 1.32f else size
    Box(
        modifier = Modifier
            .size(boxW, size)
            .clip(shape)
            .background(background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val step = if (isPrev) -3000L else 3000L
                        var seekStarted = false
                        // 绑定到 PressGestureScope 的等待松手函数，供子协程调用
                        val awaitRelease: suspend () -> Boolean = this::tryAwaitRelease
                        val timer = scope.launch {
                            delay(350) // 长按阈值：超过才进入连续 seek
                            seekStarted = true
                            val job = scope.launch {
                                onSeekDelta(step)
                                delay(120)
                                while (true) {
                                    onSeekDelta(step)
                                    delay(200)
                                }
                            }
                            awaitRelease() // 松手才停
                            job.cancel()
                        }
                        val released = tryAwaitRelease()
                        timer.cancel()
                        // 仅在「短按松手」时切歌；长按（已触发连续 seek）松手不再切歌
                        if (released && !seekStarted) onClickOnce()
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPrev) Icons.Filled.SkipPrevious else Icons.Filled.SkipNext,
            contentDescription = if (isPrev) "上一首（长按快退）" else "下一首（长按快进）",
            tint = iconColor,
            modifier = Modifier.size(size * 0.55f),
        )
    }
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

/**
 * 主题1 底部三瓣长胶囊：整体一个胶囊（两端半圆），内部三等分，
 * 左=音量、中=播放模式、右=归类列表；中间区域为方形、左右端各为半圆。
 */
@Composable
private fun TriPill(
    modifier: Modifier = Modifier,
    volumeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modeTint: Color,
    onVolume: () -> Unit,
    onMode: () -> Unit,
    onCategory: () -> Unit,
    containerColor: Color = Color(0xFF2A2A35),
) {
    val pillH = 34.dp
    Box(
        modifier = modifier
            .height(pillH)
            .clip(RoundedCornerShape(pillH / 2))
            .background(containerColor),
    ) {
        Row(Modifier.fillMaxSize()) {
            TriSection(
                icon = volumeIcon,
                tint = Color.White,
                onClick = onVolume,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.55f)
                    .align(Alignment.CenterVertically)
                    .background(Color(0x33FFFFFF)),
            )
            TriSection(
                icon = modeIcon,
                tint = modeTint,
                onClick = onMode,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.55f)
                    .align(Alignment.CenterVertically)
                    .background(Color(0x33FFFFFF)),
            )
            TriSection(
                icon = Icons.Filled.LibraryMusic,
                tint = Color.White,
                onClick = onCategory,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TriSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // detectTapGestures 不抢焦点：表冠焦点始终留在播放器根节点
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}
