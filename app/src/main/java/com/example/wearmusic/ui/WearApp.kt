package com.example.wearmusic.ui

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Scaffold
import com.example.wearmusic.PlayerViewModel
import kotlinx.coroutines.launch

/**
 * 表盘应用根：五页横向滑动
 * 0 = 播放器（正在播放）  1 = 歌词  2 = Cover Flow 专辑选曲  3 = 音乐库  4 = 音效与播放设置
 * Wear Compose 组件均对圆形表盘自适应；各列表页支持旋转表冠滚动。
 */
@Composable
fun WearMusicApp(vm: PlayerViewModel) {
    WearMusicTheme {
        val pagerState = rememberPagerState(pageCount = { 5 })
        val scope = rememberCoroutineScope()
        Scaffold {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> PlayerScreen(
                        vm = vm,
                        onOpenLyrics = { scope.launch { pagerState.animateScrollToPage(1) } }
                    )
                    1 -> LyricsScreen(vm)
                    2 -> AlbumCoverFlow(vm)
                    3 -> FileManagerScreen(vm)
                    4 -> AudioSettingsScreen(vm)
                }
            }
        }
    }
}
