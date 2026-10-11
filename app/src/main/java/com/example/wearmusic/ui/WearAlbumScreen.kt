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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.example.wearmusic.Album
import com.example.wearmusic.PlayerViewModel

private val ACCENT = Color(0xFF34C7F4)

/**
 * 手表「专辑选曲」页（iPod Classic 风格）：
 * 专辑列表 → 专辑详情（曲目）→ 点曲目即播。
 *
 * iPod 风格要点：
 * - 每行「左侧专辑名 + 歌手，右侧专辑封面」
 * - 进入专辑后先展示大封面与专辑信息，再列曲目
 */
@Composable
fun AlbumScreen(vm: PlayerViewModel) {
    // 二级导航：null = 专辑列表；非 null = 该专辑详情
    var detail by remember { mutableStateOf<Album?>(null) }

    if (detail == null) {
        AlbumListScreen(vm = vm, onOpen = { detail = it })
    } else {
        AlbumDetailScreen(vm = vm, album = detail!!, onBack = { detail = null })
    }
}

/** 专辑列表：左文字 / 右封面 */
@Composable
private fun AlbumListScreen(vm: PlayerViewModel, onOpen: (Album) -> Unit) {
    val albums by vm.albums.collectAsState()

    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "暂无专辑\n授权后自动扫描本地音乐",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester()

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .rotaryScroll(listState, focusRequester)
    ) {
        item {
            Text(
                "专辑 (${albums.size})",
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        items(albums.size) { i ->
            AlbumRow(album = albums[i], onClick = { onOpen(albums[i]) })
        }
    }
}

/** 单行专辑：专辑名 + 歌手（左），封面（右） */
@Composable
private fun AlbumRow(album: Album, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(album.name, fontSize = 13.sp, maxLines = 1)
            Text(
                "${album.artist} · ${album.trackCount} 首",
                fontSize = 10.sp,
                color = Color.Gray,
                maxLines = 1
            )
        }
        Spacer(Modifier.size(8.dp))
        AlbumArt(uri = album.albumArtUri, size = 42.dp, corner = 6.dp)
    }
}

/** 专辑详情：大封面 + 专辑信息 + 「播放全部」+ 曲目列表 */
@Composable
private fun AlbumDetailScreen(vm: PlayerViewModel, album: Album, onBack: () -> Unit) {
    val tracks by vm.tracks.collectAsState()
    val index by vm.currentIndex.collectAsState()

    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester()

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .rotaryScroll(listState, focusRequester),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            AlbumArt(uri = album.albumArtUri, size = 72.dp, corner = 10.dp)
        }
        item {
            Text(album.name, fontSize = 14.sp, maxLines = 2, textAlign = TextAlign.Center)
        }
        item {
            Text(
                "${album.artist} · ${album.trackCount} 首",
                fontSize = 10.sp,
                color = Color.Gray,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回专辑列表")
                }
                Button(onClick = { vm.playAlbum(album) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "播放全部")
                }
            }
        }
        items(album.trackIndices.size) { i ->
            val globalIndex = album.trackIndices[i]
            val t = tracks.getOrNull(globalIndex)
            val isCurrent = globalIndex == index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.selectTrack(globalIndex) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = if (isCurrent) ACCENT else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        t?.title ?: "未知曲目",
                        fontSize = 12.sp,
                        maxLines = 1,
                        color = if (isCurrent) ACCENT else Color.White
                    )
                    Text(
                        formatMs(t?.duration ?: 0L),
                        fontSize = 9.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** 专辑封面：无封面时回退为音符占位（Coil 加载失败即露出底层图标） */
@Composable
private fun AlbumArt(uri: android.net.Uri, size: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Color(0x22FFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(size / 2)
        )
        AsyncImage(
            model = uri,
            contentDescription = "专辑封面",
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(corner)),
            contentScale = ContentScale.Crop
        )
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${"%02d".format(s % 60)}"
}
