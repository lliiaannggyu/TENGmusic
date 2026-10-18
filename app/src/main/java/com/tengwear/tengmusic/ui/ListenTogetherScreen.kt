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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items as scalingItems
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text as WearText
import com.tengwear.tengmusic.PlayerViewModel
import com.tengwear.tengmusic.lan.LanEvent
import com.tengwear.tengmusic.lan.LanManager
import com.tengwear.tengmusic.lan.LanProtocol

private val LT_ACCENT = Color(0xFFB69DF8)
private val LT_BG = Color(0xF20B0B0E)
private val LT_CARD_SELECTED = Color(0x33B69DF8)

/**
 * 局域网「一起听」。
 *
 * 主播：选设备 + 多选歌曲 → 组成**临时歌单**整张推给对端 → 双方按同一顺序播放；
 * 之后主播的播放/暂停/切歌/进度每秒同步一次，嘉宾镜像（主播权威）。
 * 嘉宾：收到一次邀请（显示首数与前三首）→ 接受 → 收完整张 → 自动按序起播。
 *
 * 文件只落对方缓存目录，退出即删，不污染对方媒体库。
 */
@Composable
fun ListenTogetherScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val peers by LanManager.peers.collectAsState()
    val transfers by LanManager.transfers.collectAsState()
    val sharedMode by vm.sharedMode.collectAsState()
    val queue by vm.listenQueue.collectAsState()
    val listenIndex by vm.listenIndex.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val position by vm.position.collectAsState()
    val currentTrack by vm.currentTrack.collectAsState()
    val offer by LanManager.pendingListenOffer.collectAsState()

    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester()

    var selectedPeerId by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    // 多选：按点击顺序保存曲目 id（顺序即推送顺序）
    val picked = remember { mutableStateListOf<Long>() }

    LaunchedEffect(Unit) {
        LanManager.events.collect { ev ->
            if (ev is LanEvent.Done && ev.kind == LanProtocol.KIND_LISTEN) {
                if (!ev.sending) {
                    if (ev.ok) {
                        vm.startListenAsGuest()
                        statusMsg = null
                    } else {
                        statusMsg = "接收失败：${ev.message}"
                    }
                } else if (!ev.ok) {
                    statusMsg = "发送失败：${ev.message}"
                }
            }
        }
    }

    ScreenScaffold(scrollState = listState) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LT_BG)
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryScroll(listState, focusRequester),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable(onClick = onClose)
                        )
                        WearText("一起听", fontSize = 13.sp, color = Color.White)
                    }
                }

                // ============ 正在一起听（会话中）============
                if (sharedMode) {
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(percent = 50),
                            colors = CardDefaults.cardColors(
                                containerColor = LT_CARD_SELECTED,
                                contentColor = Color.White,
                                titleColor = LT_ACCENT,
                                subtitleColor = Color.Gray,
                            ),
                        ) {
                            WearText("正在一起听", fontSize = 11.sp, color = LT_ACCENT)
                            val t = currentTrack
                            WearText(
                                if (t != null) "《${t.title}》" else "未在播放",
                                fontSize = 10.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (t != null && t.artist.isNotBlank()) {
                                WearText(t.artist, fontSize = 8.sp, color = Color.Gray, maxLines = 1)
                            }
                            if (queue.isNotEmpty()) {
                                WearText(
                                    "${(listenIndex + 1).coerceAtMost(queue.size)}/${queue.size}" +
                                        " · ${fmt(position)}",
                                    fontSize = 8.sp,
                                    color = Color.Gray,
                                )
                                // 细进度条（当前曲目播放位置）
                                val dur = t?.duration ?: 0L
                                val frac = if (dur > 0L) {
                                    (position.toFloat() / dur).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33FFFFFF))
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(frac)
                                            .height(3.dp)
                                            .clip(CircleShape)
                                            .background(LT_ACCENT)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "上一首",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable { vm.prev() }
                                )
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "暂停" else "播放",
                                    tint = LT_ACCENT,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clickable { vm.togglePlay() }
                                )
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "下一首",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable { vm.next() }
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { vm.exitListenTogether() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x33FF6B6B)
                                ),
                            ) {
                                WearText("退出一起听", fontSize = 11.sp)
                            }
                        }
                    }
                    item {
                        WearText(
                            "切歌 / 暂停 / 进度实时同步\n退出后不保留缓存文件",
                            fontSize = 8.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ============ 收到邀请 ============
                val inv = offer
                if (inv != null) {
                    val xfer = transfers.firstOrNull { it.id == inv.transferId }
                    val receiving = xfer != null && !xfer.finished
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(percent = 50),
                            colors = CardDefaults.cardColors(
                                containerColor = LT_CARD_SELECTED,
                                contentColor = Color.White,
                                titleColor = LT_ACCENT,
                                subtitleColor = Color.Gray,
                            ),
                        ) {
                            WearText("一起听邀请", fontSize = 11.sp, color = LT_ACCENT)
                            WearText(
                                "${inv.peer.name} 邀请你听 ${inv.count} 首",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                            inv.queue.take(3).forEach { m ->
                                WearText(
                                    "· ${m.title}",
                                    fontSize = 9.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (inv.count > 3) {
                                WearText("…共 ${inv.count} 首", fontSize = 8.sp, color = Color.Gray)
                            }
                            Spacer(Modifier.height(4.dp))
                            if (receiving) {
                                val f = xfer?.fraction
                                WearText(
                                    if (f != null) "接收中 ${(f * 100).toInt()}%" else "接收中…",
                                    fontSize = 10.sp,
                                    color = LT_ACCENT
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33FFFFFF))
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(f ?: 0f)
                                            .height(3.dp)
                                            .clip(CircleShape)
                                            .background(LT_ACCENT)
                                    )
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { LanManager.respondOffer(inv.transferId, true) }
                                    ) { WearText("接受", fontSize = 10.sp) }
                                    Button(
                                        onClick = {
                                            LanManager.respondOffer(inv.transferId, false)
                                            statusMsg = "已拒绝"
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0x33FF6B6B)
                                        ),
                                    ) { WearText("拒绝", fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }

                // ============ 发起一起听：选设备 + 多选歌 ============
                if (!sharedMode && inv == null) {
                    item { ListHeader { WearText("附近设备", fontSize = 11.sp) } }
                    if (peers.isEmpty()) {
                        item {
                            WearText(
                                "未发现设备\n两台设备需在同一 Wi-Fi 下\n并都打开本页",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    scalingItems(peers) { peer ->
                        val selected = peer.id == selectedPeerId
                        Card(
                            onClick = { selectedPeerId = peer.id },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(percent = 50),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) LT_CARD_SELECTED else LT_BG,
                                contentColor = Color.White,
                                titleColor = if (selected) LT_ACCENT else Color.White,
                                subtitleColor = Color.Gray,
                            ),
                        ) {
                            WearText(
                                peer.name + if (selected) " ✓" else "",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            WearText(peer.host, fontSize = 8.sp, color = Color.Gray)
                        }
                    }

                    item {
                        ListHeader {
                            WearText(
                                "选歌（已选 ${picked.size} 首，按点击顺序）",
                                fontSize = 11.sp
                            )
                        }
                    }
                    scalingItems(tracks) { t ->
                        val on = picked.contains(t.id)
                        Card(
                            onClick = {
                                if (on) picked.remove(t.id) else picked.add(t.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(percent = 50),
                            colors = CardDefaults.cardColors(
                                containerColor = if (on) LT_CARD_SELECTED else LT_BG,
                                contentColor = Color.White,
                                titleColor = if (on) LT_ACCENT else Color.White,
                                subtitleColor = Color.Gray,
                            ),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (on) Icons.Filled.Check else Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = if (on) LT_ACCENT else Color(0x44FFFFFF),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    WearText(
                                        t.title,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    WearText(
                                        t.artist,
                                        fontSize = 8.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (on) {
                                    WearText(
                                        "${picked.indexOf(t.id) + 1}",
                                        fontSize = 9.sp,
                                        color = LT_ACCENT
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Button(
                                onClick = {
                                    val peer = peers.find { it.id == selectedPeerId }
                                    val sel = picked.toList().mapNotNull { id ->
                                        tracks.find { it.id == id }
                                    }
                                    if (peer != null && sel.isNotEmpty()) {
                                        vm.startListenSession(peer, sel)
                                        statusMsg = "已邀请 ${peer.name}，等待接受…"
                                    }
                                },
                                enabled = selectedPeerId != null && picked.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                WearText(
                                    if (picked.isEmpty()) "开始一起听" else "开始一起听（${picked.size} 首）",
                                    fontSize = 11.sp
                                )
                            }
                            WearText(
                                "只推送选中的歌，组成临时歌单\n双方按同一顺序播放",
                                fontSize = 8.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // 状态提示
                val msg = statusMsg
                if (msg != null) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            WearText(msg, fontSize = 9.sp, color = LT_ACCENT, textAlign = TextAlign.Center)
                            WearText(
                                "知道了",
                                fontSize = 9.sp,
                                color = Color.Gray,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { statusMsg = null }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 毫秒 → m:ss */
private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
