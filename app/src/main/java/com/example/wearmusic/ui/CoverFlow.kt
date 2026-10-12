package com.example.wearmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import coil.compose.AsyncImage
import com.example.wearmusic.Album
import com.example.wearmusic.PlayerViewModel
import com.example.wearmusic.coverflow.CoverFlow
import com.example.wearmusic.coverflow.CoverFlowParams
import com.example.wearmusic.coverflow.CoverFlowState
import com.example.wearmusic.coverflow.rememberCoverFlowState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 专辑选曲页（iPod Classic「Cover Flow」）—— 直接复用 GitHub 开源库 pakohan/compose-coverflow
 * 的成熟实现（已移植到本工程的 com.example.wearmusic.coverflow 包）。
 *
 * 该库提供：居中吸附、惯性滑动、3D 侧转(rotationY)、等比缩放、镜像倒影——即 iPod 的标准观感。
 * 这里只负责把「专辑」数据接入它，并加上：当前专辑名、点封面进曲目、手表表冠翻动。
 *
 * 交互：轻触已居中的专辑封面 → 直接播放整张专辑（并关闭选曲）；轻触侧面封面 → 滚到正中。
 *
 * @param sizeFraction 中间封面边长 = 容器短边 × 该比例（默认 0.5，即半屏宽，iPod 标准比例）
 * @param onBack 左上角返回 / 直接播放后关闭：回到播放器
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
    var centeredIndex by remember { mutableStateOf(-1) }
    val stateRef = remember { mutableStateOf<CoverFlowState?>(null) }

    lateinit var state: CoverFlowState
    state = rememberCoverFlowState(
        onSelectHandler = { idx -> centeredIndex = idx },
        onItemClick = { index ->
            val s = stateRef.value
            // 点「已居中」的封面 → 直接播放整张专辑并关闭选曲
            if (s != null && index == centeredIndex) {
                vm.playAlbum(albums[index])
                onBack()
            } else {
                s?.scrollToItem(index)
            }
        }
    )
    stateRef.value = state

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

        // 表冠（转动手表表冠 / 三星虚拟表冠 翻动封面）；触摸滑动同样可用
        val focusRequester = rememberRotaryFocusRequester()
        // 官方表冠旋转逻辑：用 RotaryScrollableDefaults.behavior 直接驱动 Cover Flow 的 LazyRow
        // （自动抢占焦点，实体/虚拟表冠通用）
        val rotaryBehavior = RotaryScrollableDefaults.behavior(state.lazyListState)
        // 下滑手势累积量：向下超过阈值即返回播放页
        var dragDownAcc by remember { mutableFloatStateOf(0f) }
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotaryScrollable(rotaryBehavior, focusRequester)
                // 下滑返回播放页（不与 Cover Flow 的横向翻动冲突）
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragDownAcc = 0f },
                        onDragEnd = {
                            val threshold = with(density) { 60.dp.toPx() }
                            if (dragDownAcc >= threshold) onBack()
                        },
                    ) { _, dragAmount -> dragDownAcc += dragAmount }
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

        // 底部居中：返回按钮 + 当前专辑名 / 歌手 + 轻触提示（圆形安全区内）
        // 用可被 Compose 观察的 centeredIndex（而非 state.selectedIndex 普通字段），
        // 保证「滚动到不同专辑时名字实时更新」
        val sel = if (centeredIndex >= 0) centeredIndex else 0
        val current = albums.getOrNull(sel)
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
                text = "${current?.artist ?: ""} · 轻触封面播放",
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
        // 兜底图标：加载成功会被封面遮住；加载失败（如无封面）则显示浅色专辑图标
        Icon(
            imageVector = Icons.Filled.Album,
            contentDescription = null,
            tint = Color(0x55FFFFFF),
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.Center),
        )
        AsyncImage(
            model = album.albumArtUri,
            contentDescription = album.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
