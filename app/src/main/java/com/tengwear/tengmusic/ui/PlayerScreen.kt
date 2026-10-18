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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.tengwear.tengmusic.PlayMode
import com.tengwear.tengmusic.PlayerViewModel
import com.tengwear.tengmusic.SettingsRepository
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    // 进度条样式：0 = 圆环（默认），1 = 波浪圆环（M3E 官方 CircularWavyProgressIndicator 同款）
    val progressStyle by vm.progressStyle.collectAsState()
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
    // 归类列表的两种入口：点底部「归类」键 = 播放模式；双击顶部歌名 = 把当前歌加入类别
    var categoryAssignMode by remember { mutableStateOf(false) }

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

            // 进度环（样式 0）：按屏幕短边计算直径，圆心补偿 TimeText 内缩后居中于屏幕中心，
            // 使其真正贴近圆屏边缘（文字/按钮仍按内容盒安全圆排布，避开 TimeText）。
            // 角度语义（Compose）：0°=3点钟，顺时针增大，270°=12点钟。
            // startAngle=300°（1点钟）顺时针扫到 endAngle=600°（等价 240°=11点钟），
            // 即 300°~600° 共 300° 的弧，顶部 240°~300°（11点~1点）留 60° 缺口，
            // 避开 AppScaffold 的系统时钟 TimeText，解决「时钟遮挡进度条」问题。
            // 波浪圆环（样式 1）不在此处渲染——v1.26 起改为围绕播放/暂停圆形按钮
            // 转动（见主控三键处的官方 CircularWavyProgressIndicator）。
            if (progressStyle == SettingsRepository.PROGRESS_STYLE_RING) {
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
            }

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
                                    detectTapGestures(onDoubleTap = {
                                        // 双击歌名：进入「把当前歌加入归类」模式
                                        categoryAssignMode = true
                                        showCategoryList = true
                                    })
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
                    // 官方 M3E ButtonGroup：按压任一按钮时，被按的按钮变宽、
                    // 相邻按钮被压缩推开（animateWidth + 官方 measure policy），
                    // 即 Material 3 Expressive 的挤压动画，全部官方代码。
                    // 三个按钮各自的 interactionSource：既驱动官方形状/ripple，
                    // 又通过 animateWidth 注册到 ButtonGroup 的挤压动画。
                    val prevIS = remember { MutableInteractionSource() }
                    val playIS = remember { MutableInteractionSource() }
                    val nextIS = remember { MutableInteractionSource() }
                    ButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        overflowIndicator = {},
                    ) {
                        customItem(
                            buttonGroupContent = {
                                PrevNextButton(
                                    isPrev = true,
                                    size = sideSize,
                                    pill = true,
                                    onClickOnce = { vm.prev() },
                                    onSeekDelta = vm::seekBy,
                                    background = ctrlBg,
                                    iconColor = ctrlIcon,
                                    interactionSource = prevIS,
                                    modifier = Modifier.animateWidth(prevIS),
                                )
                            },
                            menuContent = {},
                        )
                        customItem(
                            buttonGroupContent = {
                                if (progressStyle == SettingsRepository.PROGRESS_STYLE_WAVY) {
                                    // 波浪环：完全照参考图的「实心波浪盘 + 一圈波浪轨道 + 骑轨进度弧」。
                                    // 三层都由 androidx.compose.material3.CircularWavyProgressIndicator
                                    // （Material3 1.5.0-alpha18，Apache-2.0）绘制，零自绘。
                                    //
                                    // 官方绘制模型（CircularWavyProgressModifiers.kt:1338 / 1107 / 1139）：
                                    //   r = 48/2 - stroke/2；vertices = max(5, round(2πr/λ))；
                                    //   path = Morph(circle → star(N)).toPath(amplitude)；再 scale(48 - stroke)
                                    //   → 描边外缘恒等于容器边缘。故「超粗 stroke」即实心盘：
                                    //   内缘 = 24 - stroke，stroke ≥ 24 时无孔。
                                    // 圆角星形 N=8 实测峰 0.8864r / 谷 0.7844r（amp=1 → 波幅 ±5.1%）。
                                    //
                                    // 参考图径向剖面实测（328×311）：盘峰径 218 / 谷径 192；轨道中心径 259、
                                    // 径向宽 19、内缘径 240（盘外留约 11px 暗缝）；进度弧骑在轨道上，
                                    // 从 12 点起顺时针变长（窗口固定不旋转）。
                                    //
                                    // 参数换算（P = 盘峰径）：
                                    //   盘   stroke 24dp → 峰 22.64 画布单位 → scale = P/45.28（内缘 -2 → 实心）
                                    //   轨道 stroke 2.77dp → 中心线 18.88 → scale = 0.0314P（视觉宽 0.087P）
                                    //   λ = 2πr/8（锁 8 瓣）；waveSpeed = λ/2 → 两例角速度相同，波峰永不漂移
                                    val p: Float = playSize.value
                                    val scaleDisc: Float = p / 45.28f
                                    val scaleRing: Float = 0.0314f * p
                                    val strokeDisc: Float = with(density) { 24.dp.toPx() }
                                    val strokeRing: Float = with(density) { 2.77.dp.toPx() }
                                    val ringBox: Dp = playSize * 1.51f
                                    // 播放 → 振幅 1（±5.1%，与参考图一致）；暂停 → 0
                                    // （官方 Increasing/DecreasingAmplitude 缓动，平滑退化为圆）
                                    val waveAmp: (Float) -> Float = { if (isPlaying) 1f else 0f }
                                    // 盘面为不透明主题色 → 图标按亮度取深 teal / 白
                                    val wavyIcon: Color =
                                        if (themeColor.luminance() > 0.4f) {
                                            Color(0xFF06343B)
                                        } else {
                                            Color.White
                                        }
                                    Box(
                                        modifier = Modifier
                                            .size(ringBox)
                                            .animateWidth(playIS),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        // ① 实心波浪盘（stroke 24 → 内缘 -2，中心无孔）
                                        CircularWavyProgressIndicator(
                                            progress = { 1f },
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .scale(scaleDisc)
                                                .rotate(-90f),
                                            color = themeColor,
                                            trackColor = Color.Transparent,
                                            stroke = Stroke(strokeDisc, cap = StrokeCap.Round),
                                            trackStroke = Stroke(strokeDisc, cap = StrokeCap.Round),
                                            amplitude = waveAmp,
                                            wavelength = 9.42.dp,
                                            waveSpeed = 4.71.dp,
                                            gapSize = 0.dp,
                                        )
                                        // ② 整圈波浪轨道
                                        CircularWavyProgressIndicator(
                                            progress = { 1f },
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .scale(scaleRing)
                                                .rotate(-90f),
                                            color = Color(0x66FFFFFF),
                                            trackColor = Color.Transparent,
                                            stroke = Stroke(strokeRing, cap = StrokeCap.Round),
                                            trackStroke = Stroke(strokeRing, cap = StrokeCap.Round),
                                            amplitude = waveAmp,
                                            wavelength = 17.75.dp,
                                            waveSpeed = 8.88.dp,
                                            gapSize = 0.dp,
                                        )
                                        // ③ 进度弧：与②同几何 → 波形精确重合，12 点起顺时针变长
                                        CircularWavyProgressIndicator(
                                            progress = { animatedProgress },
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .scale(scaleRing)
                                                .rotate(-90f),
                                            color = themeColor,
                                            trackColor = Color.Transparent,
                                            stroke = Stroke(strokeRing, cap = StrokeCap.Round),
                                            trackStroke = Stroke(strokeRing, cap = StrokeCap.Round),
                                            amplitude = waveAmp,
                                            wavelength = 17.75.dp,
                                            waveSpeed = 8.88.dp,
                                            gapSize = 0.dp,
                                        )
                                        PlayPauseButton(
                                            isPlaying = isPlaying,
                                            onToggle = vm::togglePlay,
                                            size = playSize,
                                            pill = false,
                                            background = playBg,
                                            iconColor = wavyIcon,
                                            interactionSource = playIS,
                                        )
                                }
                                } else {
                                    PlayPauseButton(
                                        isPlaying = isPlaying,
                                        onToggle = vm::togglePlay,
                                        size = playSize,
                                        pill = false,
                                        background = playBg,
                                        iconColor = playIcon,
                                        interactionSource = playIS,
                                        modifier = Modifier.animateWidth(playIS),
                                    )
                                }
                            },
                            menuContent = {},
                        )
                        customItem(
                            buttonGroupContent = {
                                PrevNextButton(
                                    isPrev = false,
                                    size = sideSize,
                                    pill = true,
                                    onClickOnce = { vm.next() },
                                    onSeekDelta = vm::seekBy,
                                    background = ctrlBg,
                                    iconColor = ctrlIcon,
                                    interactionSource = nextIS,
                                    modifier = Modifier.animateWidth(nextIS),
                                )
                            },
                            menuContent = {},
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
                            onCategory = {
                                // 底部「归类」键：播放模式（点类别即播放）
                                categoryAssignMode = false
                                showCategoryList = true
                            },
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

        // 归类列表浮层：双击歌名（把当前歌加入类别）/ 点底部「归类」键（播放模式）唤起。
        // 待加入的曲目由 CategoryListScreen 内部响应式读取 vm.currentTrack（不再传快照，
        // 避免 ViewModel 重建后 currentIndex=-1 时快照永远为 null 导致"点击没反应"）。
        if (showCategoryList) {
            CategoryListScreen(
                vm = vm,
                onClose = { showCategoryList = false },
                mode = if (categoryAssignMode) CategoryListMode.ASSIGN else CategoryListMode.PLAY,
            )
        }
    }
}

/** 播放/暂停主按钮：M3E 官方 FilledIconButton（内置挤压动画）。
 * 官方挤压动画原理：shapes 的 shape（圆形）与 pressedShape（小圆角）在按压时
 * 由官方 rememberAnimatedShape + motion spec 平滑插值——容器形状"方圆"渐变；
 * 同一 interactionSource 经 ButtonGroup 的 animateWidth 注册后，按压时还会把
 * 相邻按钮挤开（官方 ButtonGroup measure policy），全部官方代码。
 * 注意：官方 clickable 自带 focusable，会抢走播放器根节点的表冠焦点，
 * 用 focusProperties { canFocus = false } 禁用后代获焦，表冠调节不受影响。 */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    size: Dp = 56.dp,
    background: Color = ACCENT,
    iconColor: Color = Color.White,
    pill: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onToggle,
        interactionSource = interactionSource,
        shapes = IconButtonDefaults.shapes(shape = RoundedCornerShape(percent = 50)),
        modifier = modifier
            .size(if (pill) size * 1.6f else size)
            .focusProperties { canFocus = false },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = background,
            contentColor = iconColor,
        ),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = iconColor,
            modifier = Modifier.size(if (pill) size * 0.4f else size * 0.56f),
        )
    }
}

/** 上一首 / 下一首：短按切歌；长按连续快进/快退，松手即停且不再触发切歌。
 * 容器为 M3E 官方 FilledIconButton（官方 ripple + 形状挤压动画）；
 * 交互全部走官方通道——短按切歌由官方 onClick 触发，长按 seek 由官方
 * interactionSource 的 Press/Release/Cancel 事件驱动（不再自挂 pointerInput：
 * v1.26 曾因外层 detectTapGestures 与官方 clickable 的 awaitFirstDown 竞争，
 * 导致 down 被内层消费后外层手势饿死，切歌与调进度全部失效）。
 * focusProperties 禁用获焦保住表冠焦点。 */
@Composable
private fun PrevNextButton(
    isPrev: Boolean,
    size: Dp = 44.dp,
    onClickOnce: () -> Unit,
    onSeekDelta: (Long) -> Unit,
    background: Color = CTRL_BG,
    iconColor: Color = Color.White,
    pill: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // 胶囊态刻意拉宽（宽 > 高），形成横向长条胶囊；圆形态保持正方形
    val boxW = if (pill) size * 1.32f else size
    val step = if (isPrev) -3000L else 3000L
    // 长按是否已触发连续 seek：触发过则松手不再切歌
    var longFired by remember { mutableStateOf(false) }
    var seekJob by remember { mutableStateOf<Job?>(null) }
    // 官方 interactionSource 事件流：Press → 350ms 阈值后启动连续 seek；
    // Release/Cancel（松手/手势取消）→ 立即取消，松手必停
    LaunchedEffect(interactionSource, step) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    longFired = false
                    seekJob?.cancel()
                    seekJob = scope.launch {
                        delay(350) // 长按阈值：超过才进入连续 seek
                        longFired = true
                        onSeekDelta(step)
                        while (isActive) {
                            delay(200)
                            onSeekDelta(step)
                        }
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    seekJob?.cancel()
                    seekJob = null
                }
            }
        }
    }
    FilledIconButton(
        // 短按切歌走官方 onClick；长按（longFired）松手不再触发切歌
        onClick = { if (!longFired) onClickOnce() },
        interactionSource = interactionSource,
        shapes = IconButtonDefaults.shapes(shape = RoundedCornerShape(percent = 50)),
        modifier = modifier
            .size(boxW, size)
            .focusProperties { canFocus = false },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = background,
            contentColor = iconColor,
        ),
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

/** 底部三瓣中的一节：M3E 官方 FilledIconButton（透明容器，保留三瓣长胶囊的
 * 整体视觉；按压时显示官方 ripple 与形状 morph 挤压动画）。 */
@Composable
private fun TriSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(shape = RoundedCornerShape(percent = 50)),
        modifier = modifier
            .fillMaxHeight()
            // 官方 clickable 自带 focusable：禁用获焦，表冠焦点留在播放器根节点
            .focusProperties { canFocus = false },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Transparent,
            contentColor = tint,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}
