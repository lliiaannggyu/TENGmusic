package com.example.wearmusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.example.wearmusic.PlayerViewModel

private val ACCENT = Color(0xFF34C7F4)

/** 音乐库（文件管理）：点击即播，收藏曲目带心形标记，支持旋转表冠滚动 */
@Composable
fun FileManagerScreen(vm: PlayerViewModel) {
    val tracks by vm.tracks.collectAsState()
    val index by vm.currentIndex.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester()

    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "没有音乐文件\n授权后自动扫描本地音乐",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .rotaryScroll(listState, focusRequester),
        flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(state = listState)
    ) {
        item {
            Text(
                "音乐库 (${tracks.size})",
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        items(tracks.size) { i ->
            val t = tracks[i]
            val isCurrent = i == index
            val isFavorite = favorites.contains(t.id)
            val favIcon: (@Composable BoxScope.() -> Unit)? = if (isFavorite) {
                {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "已收藏",
                        tint = Color(0xFFFF5A7A)
                    )
                }
            } else {
                null
            }
            Chip(
                onClick = { vm.selectTrack(i) },
                label = { Text(t.title, maxLines = 1, fontSize = 12.sp) },
                secondaryLabel = { Text(t.artist, maxLines = 1, fontSize = 10.sp) },
                icon = favIcon,
                colors = if (isCurrent) {
                    ChipDefaults.chipColors(contentColor = ACCENT)
                } else {
                    ChipDefaults.chipColors()
                }
            )
        }
    }
}
