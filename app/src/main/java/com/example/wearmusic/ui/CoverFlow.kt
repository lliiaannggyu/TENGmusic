package com.example.wearmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.example.wearmusic.Album
import com.example.wearmusic.PlayerViewModel
import com.example.wearmusic.coverflow.CoverFlow
import com.example.wearmusic.coverflow.CoverFlowParams
import com.example.wearmusic.coverflow.CoverFlowState
import com.example.wearmusic.coverflow.rememberCoverFlowState

/**
 * 专辑选曲页（iPod Classic「Cover Flow」）—— 直接复用 GitHub 开源库 pakohan/compose-coverflow
 * 的成熟实现（已移植到本工程的 com.example.wearmusic.coverflow 包）。
 *
 * 该库提供：居中吸附、惯性滑动、3D 侧转(rotationY)、等比缩放、镜像倒影——即 iPod 的标准观感。
 * 这里只负责把「专辑」数据接入它，并加上：当前专辑名、点封面进曲目、手表表冠翻动。
 *
 * @param sizeFraction 中间封面边长 = 容器短边 × 该比例（默认 0.5，即半屏宽，iPod 标准比例）
 * @param onBack 左上角返回：回到播放器（下滑手势之外的兜底入口）
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AlbumCoverFlow(
    vm: PlayerViewModel,
    modifier: Modifier = Modifier,
    sizeFraction: Float = 0.5f,
    onBack: () -> Unit = {},
) {
    val albums by vm.albums.collectAsState()
    var opened by remember { mutableStateOf<Album?>(null) }

    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFF06070A)),
        contentAlignment = Alignment.Center
    ) {
        if (albums.isEmpty()) {
            Text(
                "暂无专辑\n授权后自动扫描本地音乐",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            return@Box
        }

        // 表冠（转动手表表冠翻动封面）；触摸滑动同样可用
        val focusRequester = remember { FocusRequester() }
        val stateRef = remember { mutableStateOf<CoverFlowState?>(null) }
        val centeredIndex = remember { mutableStateOf(-1) }
        lateinit var state: com.example.wearmusic.coverflow.CoverFlowState
        state = rememberCoverFlowState(
            onSelectHandler = { idx -> centeredIndex.value = idx },
            onItemClick = { index ->
                val s = stateRef.value
                // 点「已居中」的封面 → 打开曲目；点侧面封面 → 滚到正中
                if (s != null && index == centeredIndex.value) opened = albums[index]
                else s?.scrollToItem(index)
            }
        )
        stateRef.value = state

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onRotaryScrollEvent { event ->
                    val dir = if (event.verticalScrollPixels > 0) 1 else -1
                    val cur = if (state.selectedIndex >= 0) state.selectedIndex else 0
                    val next = (cur + dir).coerceIn(0, albums.lastIndex)
                    if (next != cur) state.scrollToItem(next)
                    true
                }
        ) {
            CoverFlow(
                state = state,
                modifier = Modifier.fillMaxSize(),
                params = CoverFlowParams(
                    size = sizeFraction,
                    offset = 0.4f,
                    angle = 55f,
                    shift = 0.4f,
                    zoom = 0.8f,
                    mirror = true,
                ),
            ) {
                items(albums) { album ->
                    AlbumArtContent(album = album)
                }
            }
        }

        if (opened != null) {
            AlbumTracksSheet(album = opened!!, vm = vm, onClose = { opened = null })
        }

        // 底部居中：返回按钮 + 当前专辑名 / 歌手（圆形安全区内）
        val current = albums.getOrNull(state.selectedIndex.coerceAtLeast(0))
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = current?.name ?: "",
                fontSize = 13.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = "${current?.artist ?: ""} · ${current?.trackCount ?: 0} 首",
                fontSize = 10.sp,
                color = Color.Gray,
                maxLines = 1
            )
        }
    }
}

/** 单张专辑封面内容：铺满方形封面 + 轻微边框（iPod 封面观感） */
@Composable
private fun AlbumArtContent(album: Album) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(Color(0xFF161616))
            .border(1.dp, Color(0x33FFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
    ) {
        AsyncImage(
            model = album.albumArtUri,
            contentDescription = album.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

/** 点击封面后：专辑曲目列表（点曲目即播） */
@Composable
private fun AlbumTracksSheet(album: Album, vm: PlayerViewModel, onClose: () -> Unit) {
    val tracks by vm.tracks.collectAsState()
    val index by vm.currentIndex.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0000000)),
        contentAlignment = Alignment.Center
    ) {
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
                Text(
                    album.name, fontSize = 14.sp, maxLines = 1,
                    textAlign = TextAlign.Center, color = Color.White
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回封面流")
                    }
                    Button(
                        onClick = { vm.playAlbum(album) },
                        modifier = Modifier.size(40.dp)
                    ) {
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
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrent) FLOW_ACCENT else Color.Gray,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        "${i + 1}. ${t?.title ?: "未知曲目"}",
                        fontSize = 11.sp,
                        maxLines = 1,
                        color = if (isCurrent) FLOW_ACCENT else Color.White,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

private val FLOW_ACCENT = Color(0xFF34C7F4)
