package com.tengwear.tengmusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.tengwear.tengmusic.Album
import com.tengwear.tengmusic.PlayerViewModel
import com.tengwear.tengmusic.StoreAuth
import com.tengwear.tengmusic.lan.LanManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 手表应用根：圆形表盘外框 + 横向翻页导航 + 覆盖层选曲。
 *
 * 页面与覆盖层（v1.47）：
 *  - 横向翻页：0 播放器 → 1 歌词 → 2 在线搜索 → 3 设置
 *  - 上滑屏幕 / 点提示：专辑流（覆盖层，按专辑浏览；点专辑 → 歌曲列表覆盖层）
 *  - 点歌：歌名从列表位置线性飞行到播放器标题位置，同时播放器整页从模糊到清晰出现，
 *    专辑列表背景模糊消失 —— 动画与页面切换并行衔接，结束后无卡顿
 *  - 播放页下滑：打开「搜索与统计」页（上滑原路返回）
 *  - 设置 → 自定义专辑：专辑管理覆盖层（封面选择用内置文件管理器）
 */
@Composable
fun WearMusicApp(vm: PlayerViewModel) {
    WearMusicTheme {
        AppScaffold {
            Box(modifier = Modifier.fillMaxSize()) {
                val pager = rememberPagerState(pageCount = { 4 })
                var showTrackList by remember { mutableStateOf(false) }
                var showAlbumFlow by remember { mutableStateOf(false) }
                var openAlbum by remember { mutableStateOf<Album?>(null) }
                var showStats by remember { mutableStateOf(false) }
                var showCustomAlbumMgr by remember { mutableStateOf(false) }
                var showThemeManager by remember { mutableStateOf(false) }
                // 自定义专辑封面选择：待换封面的专辑 id（null = 本次文件管理器是封面工具用的）
                var pickCoverAlbumId by remember { mutableStateOf<Long?>(null) }
                var showFilePicker by remember { mutableStateOf(false) }
                var showCoverTool by remember { mutableStateOf(false) }
                var showLanTransfer by remember { mutableStateOf(false) }
                var showListenTogether by remember { mutableStateOf(false) }

                val currentPage = pager.currentPage
                val scope = rememberCoroutineScope()

                // 商店授权登录状态（本地绑定，离线可用）；未登录的功能入口统一引导登录
                val loggedIn by StoreAuth.loggedIn.collectAsState()
                val appCtx = LocalContext.current
                val onRequireLogin: () -> Unit = {
                    StoreAuth.openAuth(appCtx)
                    android.widget.Toast.makeText(appCtx, "请先通过 TENC 商店授权登录", android.widget.Toast.LENGTH_SHORT).show()
                }

                // ===== 点歌飞行动画：歌名从列表位置线性飞到播放器标题位置 =====
                var flyTitle by remember { mutableStateOf<String?>(null) }
                val flyProgress = remember { Animatable(0f) }
                // 播放器整页模糊度：点歌时 1（很模糊）→ 0（清晰），与飞行动画同步
                val playerBlur = remember { mutableFloatStateOf(0f) }

                val pendingSongOffer by LanManager.pendingSongOffer.collectAsState()
                val pendingListenOffer by LanManager.pendingListenOffer.collectAsState()
                LaunchedEffect(pendingSongOffer) {
                    if (pendingSongOffer != null) showLanTransfer = true
                }
                LaunchedEffect(pendingListenOffer) {
                    if (pendingListenOffer != null) showListenTogether = true
                }

                /** 点歌统一入口：播放 + 歌名飞行 + 播放器模糊出现 + 覆盖层模糊退场 */
                fun playWithFly(globalIndex: Int, title: String) {
                    vm.selectTrack(globalIndex)
                    scope.launch {
                        flyProgress.snapTo(0f)
                        flyTitle = title
                        // 飞行 450ms 线性；播放器模糊度同步 1→0（进度条/控件等随之清晰出现）
                        flyProgress.animateTo(1f, tween(durationMillis = 450, easing = LinearEasing))
                        flyTitle = null
                    }
                    scope.launch {
                        val steps = 30
                        for (i in steps downTo 0) {
                            playerBlur.floatValue = i / steps.toFloat()
                            kotlinx.coroutines.delay(15) // 450ms 线性退模糊
                        }
                    }
                }

                // 返回键：从最上层逐层退出
                BackHandler(enabled = showThemeManager) { showThemeManager = false }
                BackHandler(enabled = !showThemeManager && showFilePicker) { showFilePicker = false }
                BackHandler(enabled = !showFilePicker && pickCoverAlbumId != null) {
                    pickCoverAlbumId = null; showCustomAlbumMgr = false
                }
                BackHandler(enabled = !showFilePicker && pickCoverAlbumId == null && showCustomAlbumMgr) {
                    showCustomAlbumMgr = false
                }
                BackHandler(enabled = !showFilePicker && pickCoverAlbumId == null && !showCustomAlbumMgr && openAlbum != null) {
                    openAlbum = null
                }
                BackHandler(enabled = !showFilePicker && pickCoverAlbumId == null && !showCustomAlbumMgr && openAlbum == null && showAlbumFlow) {
                    showAlbumFlow = false
                }
                BackHandler(enabled = !showFilePicker && pickCoverAlbumId == null && !showCustomAlbumMgr && openAlbum == null && !showAlbumFlow && showStats) {
                    showStats = false
                }
                BackHandler(enabled = !showFilePicker && pickCoverAlbumId == null && !showCustomAlbumMgr && openAlbum == null && !showAlbumFlow && !showStats && showTrackList) {
                    showTrackList = false
                }
                BackHandler(enabled = !showFilePicker && pickCoverAlbumId == null && !showCustomAlbumMgr && openAlbum == null && !showAlbumFlow && !showStats && !showTrackList && showCoverTool) {
                    showCoverTool = false
                }
                BackHandler(enabled = !showFilePicker && pickCoverAlbumId == null && !showCustomAlbumMgr && openAlbum == null && !showAlbumFlow && !showStats && !showTrackList && !showCoverTool && showLanTransfer) {
                    showLanTransfer = false
                }
                BackHandler(enabled = !showFilePicker && pickCoverAlbumId == null && !showCustomAlbumMgr && openAlbum == null && !showAlbumFlow && !showStats && !showTrackList && !showCoverTool && !showLanTransfer && showListenTogether) {
                    showListenTogether = false
                }
                BackHandler(enabled = !showFilePicker && pickCoverAlbumId == null && !showCustomAlbumMgr && openAlbum == null && !showAlbumFlow && !showStats && !showTrackList && !showCoverTool && !showLanTransfer && !showListenTogether && currentPage != 0) {
                    scope.launch { pager.animateScrollToPage(0) }
                }

                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // 点歌后整页从模糊到清晰出现（线性，与歌名飞行同步）
                                .then(
                                    if (playerBlur.floatValue > 0.02f) {
                                        Modifier.blur((10.dp * playerBlur.floatValue))
                                    } else Modifier
                                )
                        ) {
                            WearPlayerScreen(
                                vm = vm,
                                onOpenTrackList = { showTrackList = true },
                                onOpenAlbumFlow = { showAlbumFlow = true },
                                // 下滑打开「搜索与统计」页（听歌时长/排行榜需登录）
                                onOpenStats = { if (loggedIn) showStats = true else onRequireLogin() },
                                active = currentPage == 0 && !showAlbumFlow && openAlbum == null && !showStats,
                            )
                        }
                        1 -> LyricsScreen(
                            vm = vm,
                            onOpenAlbumFlow = { showAlbumFlow = true },
                            active = currentPage == 1 && !showAlbumFlow,
                        )
                        2 -> OnlineSearchScreen(
                            vm = vm,
                            active = currentPage == 2 && !showAlbumFlow,
                        )
                        else -> AudioSettingsScreen(
                            vm = vm,
                            onOpenAlbumFlow = { showAlbumFlow = true },
                            onOpenCoverTool = { showCoverTool = true; pickCoverAlbumId = null },
                            onOpenLanTransfer = { showLanTransfer = true },
                            onOpenListenTogether = { showListenTogether = true },
                            onOpenCustomAlbums = { showCustomAlbumMgr = true },
                            onOpenThemeManager = { showThemeManager = true },
                            loggedIn = loggedIn,
                            onRequireLogin = onRequireLogin,
                            active = currentPage == 3 && !showAlbumFlow && !showCoverTool && !showCustomAlbumMgr && !showThemeManager,
                        )
                    }
                }

                if (showTrackList) {
                    WearTrackListOverlay(vm = vm, onClose = { showTrackList = false })
                }
                if (showCoverTool) {
                    CoverToolScreen(vm = vm, onClose = { showCoverTool = false })
                }
                if (showLanTransfer) {
                    LanTransferScreen(vm = vm, onClose = { showLanTransfer = false })
                }
                if (showListenTogether) {
                    ListenTogetherScreen(vm = vm, onClose = { showListenTogether = false })
                }

                // ===== 搜索与统计页：播放页下滑打开（从顶部滑入），上滑原路滑回 =====
                AnimatedVisibility(
                    visible = showStats,
                    // 淡入 + 小幅反向滑入：覆盖层快速铺满盖住底层，避免过渡帧
                    // 露出底下的 HorizontalPager 页面造成「其他页面一闪而过」。
                    enter = fadeIn(tween(180)) + slideInVertically(
                        animationSpec = tween(200), initialOffsetY = { -it / 10 }
                    ),
                    // 原路返回：往顶部小幅滑出 + 淡出
                    exit = fadeOut(tween(150)) + slideOutVertically(
                        animationSpec = tween(180), targetOffsetY = { -it / 10 }
                    ),
                ) {
                    SearchStatsScreen(
                        vm = vm,
                        onBack = { showStats = false },
                        active = showStats,
                    )
                }

                // ===== 专辑流（按专辑浏览）=====
                AnimatedVisibility(
                    visible = showAlbumFlow,
                    // 淡入 + 小幅滑入：覆盖层快速铺满盖住底层 pager 页面，避免
                    // 整屏从外滑入时露出别的页面一闪而过；展开动画仍按 3/5 处交叠启动。
                    enter = fadeIn(tween(180)) + slideInVertically(
                        initialOffsetY = { it / 10 },
                        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
                    ),
                    exit = ExitTransition.None,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AlbumCoverFlow(
                            vm = vm,
                            modifier = Modifier.fillMaxSize(),
                            onBack = { showAlbumFlow = false },
                            onOpenAlbum = { showAlbumFlow = true; openAlbum = it },
                            active = showAlbumFlow && openAlbum == null,
                        )
                    }
                }

                // ===== 专辑详情（封面放大模糊做背景 + 曲目列表）=====
                openAlbum?.let { album ->
                    AlbumDetailOverlay(
                        vm = vm,
                        album = album,
                        onBack = { openAlbum = null },
                        onPlayTrack = { idx, title ->
                            playWithFly(idx, title)
                            // 覆盖层背景反向动画（~320ms）走完后，连同专辑流一起关闭，
                            // 播放器页的模糊出现动画（450ms）刚好衔接完成，无卡顿
                            scope.launch {
                                kotlinx.coroutines.delay(360)
                                openAlbum = null
                                showAlbumFlow = false
                            }
                        },
                        onEditCustom = { showCustomAlbumMgr = true },
                        active = showAlbumFlow,
                    )
                }

                // ===== 自定义专辑管理 =====
                AnimatedVisibility(
                    visible = showCustomAlbumMgr,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                ) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF06070A))) {
                        CustomAlbumManageScreen(
                            vm = vm,
                            onBack = { showCustomAlbumMgr = false },
                            onPickCover = { albumId ->
                                pickCoverAlbumId = albumId
                                showFilePicker = true
                            },
                            active = showCustomAlbumMgr && !showFilePicker,
                        )
                    }
                }

                // ===== 主题工坊：自定义播放器UI/歌词主题 =====
                AnimatedVisibility(
                    visible = showThemeManager,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                ) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF06070A))) {
                        ThemeManagerScreen(
                            vm = vm,
                            onClose = { showThemeManager = false },
                            active = showThemeManager,
                        )
                    }
                }

                // ===== 内置文件管理器：封面选择（记录上次目录）=====
                if (showFilePicker) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF06070A))) {
                        FilePickerScreen(
                            onResult = { files ->
                                if (pickCoverAlbumId != null) {
                                    files.firstOrNull()?.let {
                                        vm.setCustomAlbumCover(
                                            pickCoverAlbumId!!,
                                            android.net.Uri.fromFile(it)
                                        )
                                    }
                                }
                                showFilePicker = false
                            },
                            onClose = { showFilePicker = false },
                        )
                    }
                }

                // ===== 歌名飞行动画 overlay：从列表位置线性飞向播放器标题位置 =====
                flyTitle?.let { title ->
                    val p = flyProgress.value
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val startY = maxHeight * 0.80f
                        val endY = maxHeight * 0.12f
                        val y = startY + (endY - startY) * p
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(0, y.roundToPx()) },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.wear.compose.material3.Text(
                                title,
                                fontSize = (14f - 3f * p).sp,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
