package com.example.wearmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import coil.compose.AsyncImage
import androidx.media3.common.Player
import com.example.wearmusic.Album
import com.example.wearmusic.EqPresets
import com.example.wearmusic.PlayerViewModel
import kotlinx.coroutines.launch

private val ACCENT = Color(0xFF34C7F4)

/**
 * 手机端（普通安卓）根界面：Material3 + 三页横向滑动
 * 与手表端 WearMusicApp 共享同一 PlayerViewModel / 播放服务。
 */
@Composable
fun PhoneApp(vm: PlayerViewModel) {
    MaterialTheme {
        val pages = listOf("播放器", "歌词", "专辑", "音乐库", "音效")
        val pagerState = rememberPagerState(pageCount = { pages.size })
        val scope = rememberCoroutineScope()
        // 当前页（用于 TabRow 高亮），随滑动更新
        var currentPage by remember { mutableStateOf(0) }
        LaunchedEffect(pagerState.currentPage) { currentPage = pagerState.currentPage }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    pages.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            icon = {
                                Icon(imageVector = pageIcon(index), contentDescription = title)
                            },
                            label = { Text(title, fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) { page ->
                when (page) {
                    0 -> PhonePlayerScreen(vm)
                    1 -> PhoneLyricsScreen(vm)
                    2 -> PhoneAlbumScreen(vm)
                    3 -> PhoneFileScreen(vm)
                    4 -> PhoneAudioSettingsScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun PhonePlayerScreen(vm: PlayerViewModel) {
    val tracks by vm.tracks.collectAsState()
    val index by vm.currentIndex.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val position by vm.position.collectAsState()
    val duration by vm.duration.collectAsState()
    val current = tracks.getOrNull(index)
    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = current?.title ?: "未选择音乐",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1
        )
        Text(
            text = current?.artist ?: "在「音乐库」页选择",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            maxLines = 1
        )

        Spacer(Modifier.height(24.dp))

        PhoneProgressBar(progress = progress, onSeek = vm::seekToFraction)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatMs(position), color = Color.Gray, fontSize = 11.sp)
            Text(formatMs(duration), color = Color.Gray, fontSize = 11.sp)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = vm::prev) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首")
            }
            FloatingActionButton(onClick = vm::togglePlay) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放"
                )
            }
            IconButton(onClick = vm::next) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
            }
        }
    }
}

@Composable
private fun PhoneLyricsScreen(vm: PlayerViewModel) {
    val lyrics by vm.lyrics.collectAsState()
    val idx by vm.lyricIndex.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(idx) {
        if (idx in lyrics.indices) listState.animateScrollToItem(idx)
    }

    if (lyrics.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "暂无歌词\n在音乐库页匹配同名 .lrc",
                textAlign = TextAlign.Center,
                color = Color.Gray
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Spacer(Modifier.height(60.dp)) }
        items(lyrics.size) { i ->
            Text(
                text = lyrics[i].text,
                textAlign = TextAlign.Center,
                style = if (i == idx) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = if (i == idx) ACCENT else Color.Gray,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        item { Spacer(Modifier.height(60.dp)) }
    }
}

@Composable
private fun PhoneFileScreen(vm: PlayerViewModel) {
    val tracks by vm.tracks.collectAsState()
    val index by vm.currentIndex.collectAsState()

    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "没有音乐文件\n授权后自动扫描本地音乐",
                textAlign = TextAlign.Center,
                color = Color.Gray
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        item {
            Text(
                "音乐库 (${tracks.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
        items(tracks.size) { i ->
            val t = tracks[i]
            val isCurrent = i == index
            ListItem(
                headlineContent = { Text(t.title, maxLines = 1) },
                supportingContent = { Text(t.artist, maxLines = 1) },
                leadingContent = {
                    Icon(
                        if (isCurrent) Icons.Filled.Pause else Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrent) ACCENT else Color.Gray
                    )
                },
                modifier = Modifier.clickable { vm.selectTrack(i) }
            )
        }
    }
}

/**
 * 手机端「专辑选曲」页：与手表端共用同一套 iPod Classic「Cover Flow」。
 * 横向连续 3D 封面流（中间正面、两侧透视倾斜 + 倒影）→ 点封面弹出曲目列表选曲。
 */
@Composable
private fun PhoneAlbumScreen(vm: PlayerViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F))
    ) {
        AlbumCoverFlow(vm = vm, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun PhoneAlbumDetail(vm: PlayerViewModel, album: Album, onBack: () -> Unit) {
    val tracks by vm.tracks.collectAsState()
    val index by vm.currentIndex.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回专辑列表")
                }
                Text(
                    album.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            PhoneAlbumArt(uri = album.albumArtUri, size = 150.dp, corner = 12.dp)
        }
        item {
            Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Text(
                "${album.trackCount} 首",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        item {
            Button(onClick = { vm.playAlbum(album) }, modifier = Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("播放全部")
            }
        }
        items(album.trackIndices.size) { i ->
            val globalIndex = album.trackIndices[i]
            val t = tracks.getOrNull(globalIndex)
            val isCurrent = globalIndex == index
            ListItem(
                headlineContent = {
                    Text("${i + 1}. ${t?.title ?: "未知曲目"}", maxLines = 1)
                },
                supportingContent = { Text(formatMs(t?.duration ?: 0L), maxLines = 1) },
                leadingContent = {
                    Icon(
                        if (isCurrent) Icons.Filled.Pause else Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrent) ACCENT else Color.Gray
                    )
                },
                modifier = Modifier.clickable { vm.selectTrack(globalIndex) }
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** 专辑封面：无封面时回退为音符占位（Coil 加载失败即露出底层图标） */
@Composable
private fun PhoneAlbumArt(
    uri: android.net.Uri,
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp
) {
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

/**
 * 手机端「音效与播放设置」：均衡器开关/预设/频段、播放模式、随机、速度、睡眠定时。
 * 与手表端共享同一套 PlayerViewModel / AudioEffects 逻辑。
 */
@Composable
private fun PhoneAudioSettingsScreen(vm: PlayerViewModel) {
    val eqEnabled by vm.eqEnabled.collectAsState()
    val eqFreqs by vm.eqFreqs.collectAsState()
    val eqGains by vm.eqGains.collectAsState()
    val eqPreset by vm.eqPreset.collectAsState()
    val repeatMode by vm.repeatMode.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val speed by vm.speed.collectAsState()
    val sleepMinutes by vm.sleepMinutes.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "均衡器",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = eqEnabled, onCheckedChange = { vm.setEqEnabled(it) })
            }
        }

        if (eqFreqs.isEmpty()) {
            item {
                Text("开始播放后均衡器自动接管音频", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            item {
                Spacer(Modifier.height(12.dp))
                Text("预设", style = MaterialTheme.typography.titleSmall)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EqPresets.ALL.size) { i ->
                        val preset = EqPresets.ALL[i]
                        FilterChip(
                            selected = preset.name == eqPreset,
                            onClick = { vm.applyPreset(preset) },
                            label = { Text(preset.name) }
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text("频段增益", style = MaterialTheme.typography.titleSmall)
            }
            items(eqFreqs.size) { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatHz(eqFreqs[i]),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.width(46.dp)
                    )
                    Slider(
                        value = ((eqGains[i] + 1500f) / 3000f).coerceIn(0f, 1f),
                        onValueChange = { vm.setBandGain(i, ((it * 3000f) - 1500f).toInt()) },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT)
                    )
                    Text(
                        "${eqGains[i] / 100}dB",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text("播放", style = MaterialTheme.typography.titleSmall)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::cycleRepeatMode) {
                    Text(
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> "单曲循环"
                            Player.REPEAT_MODE_ALL -> "列表循环"
                            else -> "顺序播放"
                        }
                    )
                }
                Button(onClick = vm::toggleShuffle) {
                    Text(if (shuffle) "随机开" else "随机关")
                }
                Button(onClick = { vm.setSpeed(nextSpeedValue(speed)) }) {
                    Text("${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x")
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Text("睡眠定时", style = MaterialTheme.typography.titleSmall)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 15, 30, 60).forEach { minutes ->
                    Button(onClick = { vm.setSleepTimer(minutes) }) {
                        Text(
                            when {
                                minutes == 0 -> "关闭"
                                sleepMinutes == minutes -> "$minutes 分"
                                else -> "$minutes 分"
                            }
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** 底部导航项图标 */
private fun pageIcon(index: Int): ImageVector = when (index) {
    0 -> Icons.Filled.MusicNote
    1 -> Icons.Filled.QueueMusic
    2 -> Icons.Filled.Album
    3 -> Icons.Filled.LibraryMusic
    else -> Icons.Filled.Settings
}

/**
 * 可点击 / 可拖动的进度条（替代原来的细线进度条 + 滑块组合）：
 * 圆角轨道 + 渐变已播部分 + 白色圆点滑块，点击或拖动任意位置即可定位。
 */
@Composable
private fun PhoneProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val shown = (if (dragging) dragValue else progress).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeek(dragValue)
                        dragging = false
                    },
                    onDragCancel = { dragging = false }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = maxWidth
        // 轨道
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFE4E4E4))
        )
        // 已播放部分（渐变）
        Box(
            modifier = Modifier
                .width(trackWidth * shown)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(ACCENT, Color(0xFF6C4CF1))))
        )
        // 滑块
        Box(
            modifier = Modifier
                .offset(x = (trackWidth * shown) - 8.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, ACCENT, CircleShape)
        )
    }
}

private fun formatHz(hz: Int): String = if (hz >= 1000) "${hz / 1000}k" else "$hz"

private fun nextSpeedValue(cur: Float): Float {
    val steps = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    return steps.firstOrNull { it > cur + 0.01f } ?: steps.first()
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${"%02d".format(s % 60)}"
}
