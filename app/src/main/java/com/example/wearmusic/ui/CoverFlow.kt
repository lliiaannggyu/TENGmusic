package com.example.wearmusic.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyListState
import androidx.wear.compose.material3.Icon
import kotlin.math.abs
import kotlinx.coroutines.delay
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wearmusic.Album
import com.example.wearmusic.PlayerViewModel
import com.example.wearmusic.coverflow.CoverFlow
import com.example.wearmusic.coverflow.CoverFlowParams
import com.example.wearmusic.coverflow.CoverFlowState
import com.example.wearmusic.coverflow.rememberCoverFlowState
import kotlin.math.abs

/**
 * 专辑选曲页（iPod Classic「Cover Flow」）—— 直接复用 GitHub 开源库 pakohan/compose-coverflow
 * 的成熟实现（已移植到本工程的 com.example.wearmusic.coverflow 包）。
 *
 * 该库提供：居中吸附、惯性滑动、3D 侧转(rotationY)、等比缩放、镜像倒影——即 iPod 的标准观感。
 * 这里只负责把「专辑」数据接入它，并加上：当前专辑名、点封面进曲目、手表表冠翻动。
 *
 * 交互：轻触已居中的专辑封面 → 直接播放整张专辑（并关闭选曲）；轻触侧面封面 → 滚到正中。
 *
 * 「当前专辑索引」不再依赖库内部滞后的 onSelectHandler（翻动快时名字会回落到专辑 0、要循环
 * 一遍才对），而是**直接由 LazyListState 的布局信息实时算出最靠近屏幕中心的专辑**，稳定可靠。
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

    // 实时、可靠地算出「最靠近屏幕中心的专辑」索引（替代库里滞后的 selectedIndex）。
    // 列表里首尾各有一个占位 spacer，所以 列表下标 - 1 = 专辑下标。
    // 先声明，供下方 onItemClick 回调引用。
    val centeredIndex = remember { mutableStateOf(0) }

    // 用可空 var 持有 state，避免在 rememberCoverFlowState 的回调里引用尚未赋值的局部 val
    var state: CoverFlowState? = null
    state = rememberCoverFlowState(
        onItemClick = { index ->
            val s = state
            // 点「已居中」的封面 → 直接播放整张专辑并关闭选曲
            if (s != null && index == centeredIndex.value) {
                vm.playAlbum(albums[index])
                onBack()
            } else {
                s?.scrollToItem(index)
            }
        }
    )

    LaunchedEffect(state) {
        val listState = state?.lazyListState ?: return@LaunchedEffect
        // 轮询布局信息，实时算出最靠近中心的专辑下标（无需 snapshotFlow，兼容性更好）
        while (true) {
            centeredIndex.value = computeCenteredAlbumIndex(listState, albums.size)
            delay(60)
        }
    }

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
        // 旋转累积量：按阈值换算成「翻一张」的步长。库内部的 free-scroll 表冠会让封面停在
        // 两张之间、不回正，这里改为累积到阈值就调用库的 scrollToItem（吸附居中），保证
        // 表冠每咔哒一格 = 翻一张且必然正中对齐。
        var rotaryAcc by remember { mutableFloatStateOf(0f) }
        // 下滑手势累积量：向下超过阈值即返回播放页
        var dragDownAcc by remember { mutableFloatStateOf(0f) }
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onRotaryScrollEvent { event ->
                    rotaryAcc += event.verticalScrollPixels
                    val step = 30f
                    var cur = centeredIndex.value
                    while (abs(rotaryAcc) >= step) {
                        val dir = if (rotaryAcc > 0f) 1 else -1
                        val next = (cur + dir).coerceIn(0, albums.lastIndex)
                        rotaryAcc -= dir * step
                        if (next != cur) {
                            cur = next
                            state?.scrollToItem(next)
                        } else {
                            rotaryAcc = 0f
                        }
                    }
                    true
                }
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
        // 名字颜色随封面明暗自适应（亮封面→深色字，暗封面→白色字）
        val currentAlbum = albums.getOrNull(centeredIndex.value)
        val isLight = rememberCoverIsLight(currentAlbum?.albumArtUri)
        val nameColor = if (isLight) Color(0xFF0E0E12) else Color.White
        val subColor = if (isLight) Color(0x990E0E12) else Color(0xFFB0B0C0)
        // 名字底衬：亮封面用浅色衬底保证深色字可读，暗封面用极淡深色衬底
        val pillBg = if (isLight) Color(0xEEFFFFFF) else Color(0x33000000)

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
            Box(
                modifier = Modifier
                    .background(pillBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentAlbum?.name ?: "",
                        fontSize = 13.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        color = nameColor,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Text(
                        text = "${currentAlbum?.artist ?: ""} · 轻触封面播放",
                        fontSize = 10.sp,
                        color = subColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 根据 LazyListState 的可见项布局，找出几何中心最靠近视口中心的项，
 * 再换算成专辑下标（列表首尾各有一个占位 spacer，故 列表下标 - 1 = 专辑下标）。
 * 比库内部基于 onGloballyPositioned 的 isSelected 回调更及时、更准，杜绝翻动时名字错位。
 */
private fun computeCenteredAlbumIndex(listState: LazyListState, albumCount: Int): Int {
    if (albumCount <= 0) return 0
    val layout = listState.layoutInfo
    val viewportCenter = layout.viewportSize.width / 2f
    var best = -1
    var bestDist = Float.MAX_VALUE
    for (item in layout.visibleItemsInfo) {
        val center = item.offset + item.size / 2f
        val d = abs(center - viewportCenter)
        if (d < bestDist) {
            bestDist = d
            best = item.index
        }
    }
    return if (best < 0) 0 else (best - 1).coerceIn(0, albumCount - 1)
}

/**
 * 加载居中专辑封面并估算平均亮度，判断封面是偏亮还是偏暗。
 * 偏亮（平均亮度 > 0.6）→ 返回 true，名字用深色；否则用白色。
 * 加载失败（无封面等）→ 默认 false（视为暗封面，白色字）。
 */
@Composable
private fun rememberCoverIsLight(uri: Uri?): Boolean {
    val context = LocalContext.current
    return produceState(initialValue = false, key1 = uri) {
        value = false
        if (uri != null) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(24, 24)
                    .allowHardware(false)
                    .build()
                val drawable = Coil.imageLoader(context).execute(request).drawable
                value = drawable?.let { averageLuminance(it) > 0.6f } ?: false
            } catch (_: Throwable) {
                value = false
            }
        }
    }.value
}

/** 计算 Drawable 的平均相对亮度（0=纯黑，1=纯白），缩到 16×16 后取平均以降低开销。 */
private fun averageLuminance(drawable: Drawable): Float {
    val src = if (drawable is BitmapDrawable && !drawable.bitmap.isRecycled) {
        drawable.bitmap
    } else {
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        bmp
    }
    val small = Bitmap.createScaledBitmap(src, 16, 16, true)
    var sum = 0f
    var n = 0
    for (y in 0 until 16) {
        for (x in 0 until 16) {
            val p = small.getPixel(x, y)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            n++
        }
    }
    if (src !== small && src !== (drawable as? BitmapDrawable)?.bitmap) src.recycle()
    small.recycle()
    return if (n > 0) sum / n else 0f
}

/** 单张专辑封面内容：铺满方形封面 + 轻微边框（iPod 封面观感） */
@Composable
private fun AlbumArtContent(album: Album) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF161616))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(4.dp))
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
