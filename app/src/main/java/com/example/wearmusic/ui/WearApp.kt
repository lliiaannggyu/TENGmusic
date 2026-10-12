package com.example.wearmusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.example.wearmusic.PlayerViewModel

/**
 * 手表应用根：圆形表盘外框 + 横向翻页导航 + 覆盖层选曲。
 *
 * 纯手表应用：直接铺满圆形表盘，无需为矩形手机屏做「内切正圆 + 黑边」兼容
 * （真圆手表的屏幕本就是圆的，超出圆形的内容由系统自动裁切）。
 *
 * 圆形表盘标准交互（避免手机式的「竖滑套横滑」二维手势）：
 *  - 横向翻页（左右滑）：0 播放器 → 1 歌词 → 2 设置
 *  - 播放器「列表」按钮：唤起平铺曲目列表（覆盖层）
 *  - 播放器/歌词/设置「选专辑」：上滑屏幕或点提示唤起 Cover Flow（覆盖层，轻触返回）
 *
 * 外层用 [AppScaffold] 提供圆形表盘标志性元素：顶部系统时间 [TimeText]（12 点经典位置）。
 */
@Composable
fun WearMusicApp(vm: PlayerViewModel) {
    WearMusicTheme {
        AppScaffold {
            Box(modifier = Modifier.fillMaxSize()) {
                val pager = rememberPagerState(pageCount = { 3 })
                var showTrackList by remember { mutableStateOf(false) }
                var showAlbumFlow by remember { mutableStateOf(false) }

                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> WearPlayerScreen(
                            vm = vm,
                            onOpenTrackList = { showTrackList = true },
                            onOpenAlbumFlow = { showAlbumFlow = true },
                        )
                        1 -> LyricsScreen(vm = vm, onOpenAlbumFlow = { showAlbumFlow = true })
                        else -> AudioSettingsScreen(vm = vm, onOpenAlbumFlow = { showAlbumFlow = true })
                    }
                }

                if (showTrackList) {
                    WearTrackListOverlay(vm = vm, onClose = { showTrackList = false })
                }
                AnimatedVisibility(
                    visible = showAlbumFlow,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                ) {
                    AlbumCoverFlow(
                        vm = vm,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { showAlbumFlow = false },
                    )
                }
            }
        }
    }
}
