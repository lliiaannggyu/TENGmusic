package com.tengwear.tengmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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
import com.tengwear.tengmusic.Album
import com.tengwear.tengmusic.PlayStatsRepository
import com.tengwear.tengmusic.PlayerViewModel

private val STATS_ACCENT = Color(0xFF34C7F4)

/**
 * 搜索与统计页（v1.47）：从播放页下滑打开（关闭时反向滑回）。
 *
 *  - 搜索：按 歌名 / 歌手 / 专辑 过滤本地曲库，点结果即播
 *  - 听歌时长：总时长 + 当前曲目时长（本地持久化，退出不重置）
 *  - 排行榜：歌曲 / 专辑 / 歌手 三个榜，按累计听歌时长排序
 */
@Composable
fun SearchStatsScreen(
    vm: PlayerViewModel,
    onBack: () -> Unit,
    active: Boolean = true,
) {
    val tracks by vm.tracks.collectAsState()
    val albums by vm.albums.collectAsState()
    val currentIndex by vm.currentIndex.collectAsState()
    val statsVersion by vm.playStatsVersion.collectAsState()

    var query by remember { mutableStateOf("") }
    var tabIndex by remember { mutableIntStateOf(0) } // 0 歌曲 1 专辑 2 歌手
    val q = query.trim().lowercase()
    val hits = remember(q, tracks) {
        if (q.isEmpty()) emptyList()
        else tracks.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
        }.take(20)
    }

    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester(active)
    val density = LocalDensity.current
    var dragUpAcc by remember { mutableFloatStateOf(0f) }

    fun fmt(ms: Long): String {
        val totalMin = ms / 60000
        return when {
            totalMin >= 60 -> "${totalMin / 60} 小时 ${totalMin % 60} 分钟"
            totalMin >= 1 -> "$totalMin 分钟"
            else -> "${ms / 1000} 秒"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06070A))
            .pointerInput(Unit) {
                // 上滑关闭（与打开手势方向相反，动画原路回去）
                detectVerticalDragGestures(
                    onDragStart = { dragUpAcc = 0f },
                    onDragEnd = {
                        val threshold = with(density) { 60.dp.toPx() }
                        if (dragUpAcc <= -threshold) onBack()
                    },
                ) { _, dragAmount -> dragUpAcc += dragAmount }
            },
    ) {
        // statsVersion 参与 key：听歌时长更新时榜单实时刷新
        val stats = remember(statsVersion) { vm.playStatsSnapshot() }
    val ranked: List<Pair<String, Long>> = remember(tabIndex, statsVersion, tracks, albums, stats) {
        when (tabIndex) {
            0 -> tracks.mapNotNull { t ->
                stats[t.id]?.takeIf { it > 0 }?.let { t.title to it }
            }.sortedByDescending { it.second }.take(15)
            1 -> albums.mapNotNull { a ->
                val ms = a.trackIndices.sumOf { idx ->
                    tracks.getOrNull(idx)?.let { stats[it.id] ?: 0L } ?: 0L
                }
                if (ms > 0) a.name to ms else null
            }.sortedByDescending { it.second }.take(15)
            else -> tracks.groupBy { it.artist.ifBlank { "未知歌手" } }
                .map { (artist, list) ->
                    artist to list.sumOf { stats[it.id] ?: 0L }
                }.filter { it.second > 0 }
                .sortedByDescending { it.second }.take(15)
        }
    }

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .rotaryScroll(listState, focusRequester),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    "搜索与统计",
                    fontSize = 14.sp,
                    color = STATS_ACCENT,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            // ---- 搜索框 ----
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .background(Color(0x1AFFFFFF), RoundedCornerShape(percent = 50))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search, contentDescription = null,
                        tint = Color.Gray, modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("搜歌名 / 歌手 / 专辑", fontSize = 11.sp, color = Color(0x66FFFFFF))
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 11.sp, color = Color.White),
                            cursorBrush = SolidColor(STATS_ACCENT),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close, contentDescription = "清空",
                            tint = Color.Gray, modifier = Modifier
                                .size(14.dp)
                                .clickable { query = "" },
                        )
                    }
                }
            }

            // ---- 搜索结果 ----
            if (q.isNotEmpty()) {
                item {
                    Text(
                        "搜索结果 (${hits.size})",
                        fontSize = 10.sp, color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(hits.size) { i ->
                    val t = hits[i]
                    val isCur = tracks.indexOf(t) == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.selectTrack(tracks.indexOf(t)) }
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.MusicNote, contentDescription = null,
                            tint = if (isCur) STATS_ACCENT else Color.Gray,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.title, fontSize = 11.sp, maxLines = 1, color = if (isCur) STATS_ACCENT else Color.White)
                            Text("${t.artist} · ${t.album}", fontSize = 9.sp, color = Color.Gray, maxLines = 1)
                        }
                    }
                }
            }

            // ---- 听歌时长总览 ----
            item {
                val total = stats.values.sum()
                val curTrack = tracks.getOrNull(currentIndex)
                val curMs = curTrack?.let { stats[it.id] ?: 0L } ?: 0L
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .background(Color(0x141B2A35), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                ) {
                    Text("累计听歌 $total 小时计时", fontSize = 9.sp, color = Color.Gray)
                    Text(fmt(total), fontSize = 13.sp, color = STATS_ACCENT)
                    curTrack?.let {
                        Text(
                            "当前《${it.title}》听过 $curMs",
                            fontSize = 9.sp, color = Color.Gray, maxLines = 1,
                        )
                    }
                }
            }

            // ---- 排行榜 tabs ----
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf("歌曲", "专辑", "歌手").forEachIndexed { i, label ->
                        CompactButton(
                            onClick = { tabIndex = i },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tabIndex == i) STATS_ACCENT else Color(0xFF2A2D36),
                                contentColor = if (tabIndex == i) Color(0xFF06343B) else Color.White,
                            ),
                        ) {
                            Text(label, fontSize = 10.sp)
                        }
                    }
                }
            }

            // ---- 榜单内容 ----
            if (ranked.isEmpty()) {
                item {
                    Text(
                        "还没有听歌数据\n播放音乐后这里会出现排行",
                        fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            } else {
                items(ranked.size) { i ->
                    val (name, ms) = ranked[i]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${i + 1}", fontSize = 11.sp,
                            color = if (i == 0) STATS_ACCENT else Color.Gray,
                            modifier = Modifier.width(18.dp),
                            textAlign = TextAlign.Center,
                        )
                        Text(name, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(fmt(ms), fontSize = 9.sp, color = Color.Gray)
                    }
                }
            }

            item {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A2D36), contentColor = Color.White,
                    ),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Text("返回播放器（上滑）", fontSize = 11.sp)
                }
            }
            item { Spacer(Modifier.height(14.dp)) }
        }
    }
}
