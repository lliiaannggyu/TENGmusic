package com.example.wearmusic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import com.example.wearmusic.PlayerViewModel
import kotlinx.coroutines.launch

/**
 * 手表应用根：圆形表盘外框 + 横向翻页导航 + 覆盖层选曲
 *
 * 关键：最外层强制「圆形表盘」裁剪。
 *  - 在真圆手表上：屏幕本就是圆的，此裁剪为恒等变换（无副作用）。
 *  - 在手机等矩形设备上：把整棵 UI 收进一个「内切正圆」、四角留黑，
 *    直接呈现「圆表盘 / 手表表盘」观感——满足「手机能装、UI 像圆形手表」。
 *
 * 为避免竖屏手机上下黑边把底部按键挤出圆形，表盘按「屏幕较短边」正方形居中，
 * 整棵内容都落在该正方形（即圆形）之内。
 *
 * 圆形表盘标准交互（避免手机式的「竖滑套横滑」二维手势）：
 *  - 横向翻页（左右滑）：0 播放器 → 1 歌词 → 2 设置
 *  - 播放器「列表」按钮：唤起平铺曲目列表（覆盖层）
 *  - 播放器/歌词/设置「选专辑」：唤起 Cover Flow 专辑流（覆盖层，轻触返回）
 *
 * 外层用 [AppScaffold] 提供圆形表盘标志性元素：顶部系统时间 [TimeText]（12 点经典位置）。
 */
@Composable
fun WearMusicApp(vm: PlayerViewModel) {
    WearMusicTheme {
        // 外框：黑色底，四角在矩形设备上呈现为「表盘外圈」
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints {
                // 取屏幕较短边作为表盘边长，保证内容完整落在圆形内
                val side = minOf(maxWidth, maxHeight)

                // 圆形裁剪：把整棵 UI 树收进一个正圆
                Box(
                    modifier = Modifier
                        .size(side)
                        .clip(CircleShape)
                ) {
                    AppScaffold {
                        val scope = rememberCoroutineScope()
                        val pager = rememberPagerState(pageCount = { 3 })
                        var showTrackList by remember { mutableStateOf(false) }
                        var showAlbumFlow by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxSize()) {
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
                            if (showAlbumFlow) {
                                AlbumCoverFlow(
                                    vm = vm,
                                    modifier = Modifier.fillMaxSize(),
                                    onBack = { showAlbumFlow = false },
                                )
                            }
                        }
                    }

                    // 表圈（bezel）：沿圆边画一圈极淡的环，强化「手表」质感
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = minOf(size.width, size.height) / 2f - 3.dp.toPx()
                        drawCircle(
                            color = Color(0x33FFFFFF),
                            radius = r,
                            style = Stroke(width = 2.dp.toPx()),
                            center = Offset(size.width / 2f, size.height / 2f),
                        )
                    }
                }
            }
        }
    }
}
