package com.tengwear.tengmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import com.tengwear.tengmusic.OnlineMusicApi
import com.tengwear.tengmusic.OnlineSong
import com.tengwear.tengmusic.PlayerViewModel
import com.tengwear.tengmusic.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 在线搜索页（横向翻页第 3 页：播放器 → 歌词 → 在线搜索 → 设置）。
 *
 * - 音源来自设置页「音源」分组导入的自定义 API（网易云接口兼容，可导入多个）
 * - 输入关键词搜索 → 结果列表
 * - 点结果 = 下载进本地曲库（Music/TENGMUSIC）→ 成功后自动用原播放器播放
 *   （下载进度/结果走 vm.coverMessage 提示条，与封面工具共用）
 */
@Composable
fun OnlineSearchScreen(vm: PlayerViewModel, active: Boolean) {
    val context = LocalContext.current
    val sources by vm.musicSources.collectAsState()
    val message by vm.coverMessage.collectAsState()

    var selectedIdx by remember { mutableStateOf(0) }
    var showSources by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<OnlineSong>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // 音源列表变化时把选中下标拉回有效范围
    if (selectedIdx >= sources.size) selectedIdx = 0
    val currentSource = sources.getOrNull(selectedIdx)

    fun doSearch() {
        val src = currentSource ?: return
        if (keyword.isBlank()) return
        searching = true
        error = null
        scope.launch {
            try {
                results = withContext(Dispatchers.IO) {
                    OnlineMusicApi.search(src.baseUrl, keyword.trim())
                }
                if (results.isEmpty()) error = "没有搜到「${keyword.trim()}」"
            } catch (t: Throwable) {
                error = "搜索失败：${t.message ?: t.javaClass.simpleName}"
            }
            searching = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF06070A))) {
        Column(Modifier.fillMaxSize()) {
            // 标题栏
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF98EAF9),
                    modifier = Modifier.size(16.dp),
                )
                Text("在线搜索", fontSize = 13.sp, color = Color.White)
                Text(
                    if (showSources) "收起" else "音源",
                    fontSize = 11.sp,
                    color = if (showSources) Color.Gray else Color(0xFF98EAF9),
                    modifier = Modifier
                        .clickable { showSources = !showSources }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // 操作结果/下载进度提示条（与封面工具共用 coverMessage）
            val msg = message
            if (msg != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                        .background(Color(0x33FFFFFF), RoundedCornerShape(percent = 50))
                        .clickable { vm.clearCoverMessage() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = msg,
                        fontSize = 9.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            val listState = rememberScalingLazyListState()
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ========== 音源面板（展开时） ==========
                if (showSources) {
                    item { ListHeaderInline("选择音源（在 设置→音源 里增删）") }
                    items(sources.size) { i ->
                        val s = sources[i]
                        val sel = i == selectedIdx
                        Card(
                            onClick = {
                                selectedIdx = i
                                showSources = false
                            },
                            modifier = Modifier.fillMaxWidth(0.92f),
                            shape = RoundedCornerShape(percent = 50),
                            colors = CardDefaults.cardColors(
                                containerColor = if (sel) Color(0x2298EAF9) else Color(0xF20B0B0E),
                                contentColor = Color.White,
                            ),
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    (if (sel) "✓ " else "") + s.displayName,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    s.baseUrl,
                                    fontSize = 8.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (sources.isEmpty()) {
                        item {
                            Text(
                                "还没有音源\n请到「设置 → 音源」添加 API 地址",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }
                    return@ScalingLazyColumn
                }

                // ========== 搜索框 + 音源快捷切换 ==========
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x22FFFFFF), RoundedCornerShape(percent = 50))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            if (keyword.isEmpty()) {
                                Text("输入歌名 / 歌手…", fontSize = 10.sp, color = Color.Gray)
                            }
                            BasicTextField(
                                value = keyword,
                                onValueChange = { keyword = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 10.sp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.size(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "音源：" + (currentSource?.displayName ?: "未设置"),
                                fontSize = 9.sp,
                                color = Color(0xFF98EAF9),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clickable { showSources = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                            Text(
                                "搜索",
                                fontSize = 11.sp,
                                color = if (keyword.isNotBlank() && currentSource != null) Color(0xFF98EAF9) else Color.Gray,
                                modifier = Modifier
                                    .background(
                                        if (keyword.isNotBlank() && currentSource != null) Color(0x2298EAF9) else Color(0x22FFFFFF),
                                        RoundedCornerShape(percent = 50),
                                    )
                                    .clickable(enabled = keyword.isNotBlank() && currentSource != null && !searching) {
                                        doSearch()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // ========== 结果区 ==========
                if (searching) {
                    item { Text("正在搜索…", fontSize = 10.sp, color = Color.Gray) }
                }
                error?.let { err ->
                    item {
                        Text(
                            err,
                            fontSize = 10.sp,
                            color = Color(0xFFFFB4B4),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        )
                    }
                }
                items(results) { song ->
                    Card(
                        onClick = {
                            currentSource?.let {
                                vm.downloadOnlineSong(song, it.baseUrl)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.94f),
                        shape = RoundedCornerShape(percent = 50),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xF20B0B0E),
                            contentColor = Color.White,
                        ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(song.title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = listOf(song.artist, song.album)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                Text(
                                    if (sub.isBlank()) "点击下载并用播放器播放" else sub,
                                    fontSize = 8.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                song.durationText,
                                fontSize = 9.sp,
                                color = Color.Gray,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 行内小节标题（复用 wear ListHeader 的形态） */
@Composable
private fun ListHeaderInline(text: String) {
    androidx.wear.compose.material3.ListHeader {
        androidx.wear.compose.material3.Text(text, fontSize = 10.sp, color = Color.Gray)
    }
}
