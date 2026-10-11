package com.example.wearmusic.ui

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Scaffold
import com.example.wearmusic.PlayerViewModel

/**
 * 表盘应用根：两页横向滑动
 * 0 = 播放器（首页，圆形 iPod 风）  1 = Cover Flow 专辑选曲
 *
 * 精简选择：首页只露出播放器和专辑流两类核心入口，
 * 其余（歌词/音乐库/音效）按需求从手表端移除，让首页就贴合图片所给的"圆形 iPod 播放器"。
 * 专辑流通过横向滑动切换。
 */
@Composable
fun WearMusicApp(vm: PlayerViewModel) {
    WearMusicTheme {
        val pagerState = rememberPagerState(pageCount = { 2 })
        Scaffold {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> WearPlayerScreen(vm = vm)
                    else -> AlbumCoverFlow(vm)
                }
            }
        }
    }
}
