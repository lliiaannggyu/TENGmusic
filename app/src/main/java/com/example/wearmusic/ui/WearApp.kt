package com.example.wearmusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.Scaffold
import com.example.wearmusic.PlayerViewModel
import kotlinx.coroutines.launch

/**
 * 手表应用根：二维翻页导航
 *
 * - 横向（右滑）：0 播放器 → 1 歌词 → 2 设置
 * - 纵向（上滑）：0 上面那组横页 → 1 Cover Flow 专辑流选曲（下滑返回）
 * - 播放器右下角「列表选曲」按钮唤起平铺曲目列表（覆盖层）
 *
 * 歌词页/设置页的列表会吃掉纵向手势，所以这两页顶部额外提供了「◀ 播放 · ▲ 选曲」
 * 点击入口，等价于上滑 / 左滑。
 */
@Composable
fun WearMusicApp(vm: PlayerViewModel) {
    WearMusicTheme {
        val scope = rememberCoroutineScope()
        val verticalPager = rememberPagerState(pageCount = { 2 })
        val horizontalPager = rememberPagerState(pageCount = { 3 })
        var showTrackList by remember { mutableStateOf(false) }

        val goAlbumFlow: () -> Unit = { scope.launch { verticalPager.animateScrollToPage(1) } }
        val goPlayer: () -> Unit = { scope.launch { verticalPager.animateScrollToPage(0) } }

        Scaffold {
            Box(modifier = Modifier.fillMaxSize()) {
                VerticalPager(
                    state = verticalPager,
                    modifier = Modifier.fillMaxSize()
                ) { vPage ->
                    if (vPage == 0) {
                        HorizontalPager(
                            state = horizontalPager,
                            modifier = Modifier.fillMaxSize()
                        ) { hPage ->
                            when (hPage) {
                                0 -> WearPlayerScreen(
                                    vm = vm,
                                    onOpenTrackList = { showTrackList = true },
                                    onOpenAlbumFlow = goAlbumFlow
                                )
                                1 -> LyricsScreen(vm = vm, onOpenAlbumFlow = goAlbumFlow)
                                else -> AudioSettingsScreen(vm = vm, onOpenAlbumFlow = goAlbumFlow)
                            }
                        }
                    } else {
                        AlbumCoverFlow(
                            vm = vm,
                            modifier = Modifier.fillMaxSize(),
                            onBack = goPlayer
                        )
                    }
                }

                if (showTrackList) {
                    WearTrackListOverlay(vm = vm, onClose = { showTrackList = false })
                }
            }
        }
    }
}
