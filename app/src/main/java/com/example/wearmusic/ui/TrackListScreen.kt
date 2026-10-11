package com.example.wearmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed as scalingItemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text as WearText
import com.example.wearmusic.PlayerViewModel
import com.example.wearmusic.Track

private val LIST_ACCENT = Color(0xFF34C7F4)
private val LIST_BG = Color(0xF20B0B0E)

/**
 * 手表「列表式选曲」覆盖层（播放器右下角按钮唤起）：
 * - 平铺全部曲目，当前曲目高亮并显示播放指示
 * - 已收藏曲目右侧显示红心
 * - 点击任意行立即播放该曲
 * - 支持表冠滚动；空列表时给出授权/扫描提示
 */
@Composable
fun WearTrackListOverlay(vm: PlayerViewModel, onClose: () -> Unit) {
    val tracks by vm.tracks.collectAsState()
    val index by vm.currentIndex.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester()

    LaunchedEffect(index) {
        if (index in tracks.indices) listState.animateScrollToItem(index)
    }

    ScreenScaffold(scrollState = listState) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LIST_BG)
        ) {
        if (tracks.isEmpty()) {
            WearText(
                "暂无曲目\n授权后自动扫描本地音乐",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
            Button(
                onClick = onClose,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                WearText("返回", fontSize = 11.sp)
            }
            return@Box
        }

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .rotaryScroll(listState, focusRequester),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回播放器",
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onClose)
                    )
                    WearText("曲目 ${tracks.size}", fontSize = 13.sp, color = Color.White)
                }
            }
            scalingItemsIndexed(tracks) { i, t ->
                WearTrackRow(
                    track = t,
                    order = i + 1,
                    isCurrent = i == index,
                    isFavorite = favorites.contains(t.id),
                    onClick = { vm.selectTrack(i) }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        }
    }
}

/** 单行曲目（手表） */
@Composable
private fun WearTrackRow(
    track: Track,
    order: Int,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) Color(0x2234C7F4) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WearText(
            text = "$order",
            fontSize = 9.sp,
            color = Color.Gray,
            modifier = Modifier.width(14.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            WearText(
                text = track.title,
                fontSize = 11.sp,
                color = if (isCurrent) LIST_ACCENT else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            WearText(
                text = track.artist,
                fontSize = 9.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "正在播放",
                tint = LIST_ACCENT,
                modifier = Modifier.size(13.dp)
            )
        } else if (isFavorite) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "已收藏",
                tint = Color(0xFFFF5A7A),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
