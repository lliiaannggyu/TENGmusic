package com.tengwear.tengmusic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.tengwear.tengmusic.Album
import com.tengwear.tengmusic.PlayerViewModel
import kotlinx.coroutines.launch

private fun DETAIL_ACCENT(): Color = Color(com.tengwear.tengmusic.AppTheme.accent.value)

/**
 * 专辑详情覆盖层（v1.47 动画版）：
 *
 * 打开：专辑封面**放大 + 高斯模糊**渐变铺满全屏做歌曲列表的背景，
 *       列表随动画淡入 —— 线性动画，与覆盖层出现无缝衔接。
 * 关闭：动画原路反向播放（模糊消失、封面缩回），结束后才真正移除覆盖层。
 * 点歌：先播放，同时背景模糊反向消失（列表模糊淡出）、
 *       歌名飞行动画在根层同步进行 —— 动画与页面切换并行不卡顿。
 */
@Composable
fun AlbumDetailOverlay(
    vm: PlayerViewModel,
    album: Album,
    onBack: () -> Unit,
    onPlayTrack: (globalIndex: Int, trackTitle: String) -> Unit,
    onEditCustom: (Album) -> Unit,
    active: Boolean = true,
) {
    val tracks by vm.tracks.collectAsState()
    val albums by vm.albums.collectAsState()
    val index by vm.currentIndex.collectAsState()
    val scope = rememberCoroutineScope()

    // 实时解析专辑（曲库刷新后索引不失效），找不到退回快照
    val liveAlbum = remember(album, albums) {
        albums.firstOrNull { it.customId >= 0 && it.customId == album.customId }
            ?: albums.firstOrNull { it.albumId == album.albumId && it.name == album.name }
            ?: albums.firstOrNull { it.albumId == album.albumId }
            ?: album
    }

    // 开合动画进度：1 = 完全展开；关闭/点歌时反向动画后再真正移除
    val open = remember { Animatable(0f) }
    LaunchedEffect(liveAlbum.albumId, liveAlbum.customId) {
        open.snapTo(0f)
        open.animateTo(1f, tween(durationMillis = 380, easing = LinearEasing))
    }

    fun closeAnimated() {
        scope.launch {
            open.animateTo(0f, tween(durationMillis = 320, easing = LinearEasing))
            onBack()
        }
    }

    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester(active)
    val density = LocalDensity.current
    var dragDownAcc by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06070A))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragDownAcc = 0f },
                    onDragEnd = {
                        val threshold = with(density) { 60.dp.toPx() }
                        if (dragDownAcc >= threshold) closeAnimated()
                    },
                ) { _, dragAmount -> dragDownAcc += dragAmount }
            },
    ) {
        // ===== 背景：专辑封面放大 + 高斯模糊（进度驱动，线性可逆）=====
        AsyncImage(
            model = liveAlbum.albumArtUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = open.value
                    alpha = p
                    val sc = 0.55f + 0.65f * p   // 从小到大放大
                    scaleX = sc
                    scaleY = sc
                }
                .then(
                    // 高斯模糊随动画渐入（0→22dp）；半径 0 时不挂 blur 修饰符
                    if (open.value > 0.02f) {
                        Modifier.blur((22.dp * open.value))
                    } else Modifier
                ),
        )
        // 暗化遮罩保证列表可读
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB306070A))
                .graphicsLayer { alpha = open.value }
        )

        // ===== 前景：专辑信息 + 曲目列表 =====
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = ((open.value - 0.25f) / 0.75f).coerceIn(0f, 1f) }
                .rotaryScroll(listState, focusRequester),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.height(10.dp)) }
            item {
                AsyncImage(
                    model = liveAlbum.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            item {
                Text(
                    liveAlbum.name,
                    fontSize = 14.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item {
                Text(
                    "${liveAlbum.artist} · ${liveAlbum.trackCount} 首",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    maxLines = 1,
                )
            }
            if (liveAlbum.isCustom) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        CompactButton(onClick = { onEditCustom(liveAlbum) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑自定义专辑")
                        }
                    }
                }
            }
            items(liveAlbum.trackIndices.size) { i ->
                val globalIndex = liveAlbum.trackIndices[i]
                val t = tracks.getOrNull(globalIndex)
                val isCurrent = globalIndex == index
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val title = t?.title ?: "未知曲目"
                            // 立即播放 + 根层歌名飞行动画启动（WearApp 回调）
                            onPlayTrack(globalIndex, title)
                            // 背景模糊反向消失（列表模糊淡出），完成后移除覆盖层
                            closeAnimated()
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrent) DETAIL_ACCENT() else Color.Gray,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            t?.title ?: "未知曲目",
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = if (isCurrent) DETAIL_ACCENT() else Color.White,
                        )
                        Text(
                            formatTrackMs(t?.duration ?: 0L),
                            fontSize = 9.sp,
                            color = Color.Gray,
                            maxLines = 1,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

private fun formatTrackMs(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${"%02d".format(s % 60)}"
}
