package com.tengwear.tengmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items as scalingItems
import androidx.wear.compose.foundation.lazy.itemsIndexed as scalingItemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
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

private val LAN_ACCENT = Color(0xFF34C7F4)
private val LAN_BG = Color(0xF20B0B0E)
private val LAN_CARD_SELECTED = Color(0x332ECCFF)

/**
 * 局域网「传歌」页：
 *  - 自动发现同一 Wi-Fi 下运行本 App 的设备（UDP 广播，2s 刷新）
 *  - 选设备 → 选歌曲 → 发送；超过设置阈值的文件先压缩，对方收到后自动解压
 *  - 对端传来的歌曲：弹卡询问接收/拒绝，落公共音乐目录 音乐/TENGMUSIC
 *  - 实时显示发送/接收进度
 */
@Composable
fun LanTransferScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val peers by LanManager.peers.collectAsState()
    val transfers by LanManager.transfers.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val thresholdMb by vm.lanCompressThresholdMb.collectAsState()
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester()

    var selectedPeerId by remember { mutableStateOf<String?>(null) }
    var selectedTrack by remember { mutableStateOf<Int?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    // 传入的传歌请求（StateFlow：WearApp 可能是收到它才打开本页，用状态不会丢）
    val incomingOffer by LanManager.pendingSongOffer.collectAsState()

    // 全局事件：传输结果（收到新歌后刷新本地库）
    LaunchedEffect(Unit) {
        LanManager.events.collect { ev ->
            when (ev) {
                is LanEvent.Done -> {
                    statusMsg = if (ev.ok) ev.message else "失败：${ev.message}"
                    if (ev.ok && ev.kind == LanProtocol.KIND_SONG && !ev.sending) {
                        vm.refreshLibrary()
                    }
                    LanManager.dismissTransfer(ev.transferId)
                }
                else -> {}
            }
        }
    }

    ScreenScaffold(scrollState = listState) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LAN_BG)
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryScroll(listState, focusRequester),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 标题行：返回 + 页名
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
                        WearText("传歌", fontSize = 13.sp, color = Color.White)
                    }
                }

                // ========== 收到的传歌请求（待决定）==========
                val offer = incomingOffer
                if (offer != null) {
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = LAN_CARD_SELECTED,
                                contentColor = Color.White,
                                titleColor = LAN_ACCENT,
                                subtitleColor = Color.Gray,
                            ),
                        ) {
                            WearText("收到传歌请求", fontSize = 11.sp, color = LAN_ACCENT)
                            WearText(
                                "${offer.peer.name} 想传给你：",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                            WearText(
                                offer.name + if (offer.compressed) "（压缩包）" else "",
                                fontSize = 10.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        LanManager.respondOffer(offer.transferId, true)
                                    }
                                ) { WearText("接收", fontSize = 10.sp) }
                                Button(
                                    onClick = {
                                        LanManager.respondOffer(offer.transferId, false)
                                        statusMsg = "已拒绝接收"
                                    },
                                    colors = androidx.wear.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color(0x33FF6B6B)
                                    ),
                                ) { WearText("拒绝", fontSize = 10.sp) }
                            }
                        }
                    }
                }

                // ========== 附近设备 ==========
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
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) LAN_CARD_SELECTED else LAN_BG,
                            contentColor = Color.White,
                            titleColor = if (selected) LAN_ACCENT else Color.White,
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

                // ========== 选择歌曲 ==========
                item { ListHeader { WearText("选择歌曲", fontSize = 11.sp) } }
                if (tracks.isEmpty()) {
                    item {
                        WearText(
                            "本地暂无歌曲",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                scalingItemsIndexed(tracks) { i, t ->
                    val selected = i == selectedTrack
                    Card(
                        onClick = { selectedTrack = i },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) LAN_CARD_SELECTED else LAN_BG,
                            contentColor = Color.White,
                            titleColor = if (selected) LAN_ACCENT else Color.White,
                            subtitleColor = Color.Gray,
                        ),
                    ) {
                        WearText(
                            t.title + if (selected) " ✓" else "",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        WearText(t.artist, fontSize = 8.sp, color = Color.Gray, maxLines = 1)
                    }
                }

                // ========== 发送 ==========
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Button(
                            onClick = {
                                val peer = peers.find { it.id == selectedPeerId }
                                val t = tracks.getOrNull(selectedTrack ?: -1)
                                if (peer != null && t != null) {
                                    LanManager.sendSong(peer, t, thresholdMb)
                                    selectedTrack = null
                                    statusMsg = "正在发送《${t.title}》给 ${peer.name}…"
                                }
                            },
                            enabled = selectedPeerId != null && selectedTrack != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            WearText("发送", fontSize = 12.sp)
                        }
                        WearText(
                            "超过 $thresholdMb MB 的文件会先压缩再传",
                            fontSize = 8.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ========== 传输进度 / 结果 ==========
                if (transfers.isNotEmpty()) {
                    transferList(transfers)
                }

                // ========== 状态 ==========
                val msg = statusMsg
                if (msg != null) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            WearText(msg, fontSize = 9.sp, color = LAN_ACCENT, textAlign = TextAlign.Center)
                            WearText(
                                "知道了",
                                fontSize = 9.sp,
                                color = Color.Gray,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
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

/**
 * 传输列表（传歌页 / 一起听页共用，ScalingLazyColumn 作用域内调用）：
 * 进行中的显示进度条，已完成的显示结果并在 5 秒后自动收起。
 */
internal fun ScalingLazyListScope.transferList(transfers: List<com.tengwear.tengmusic.lan.TransferState>) {
    item { ListHeader { WearText("传输", fontSize = 11.sp) } }
    scalingItems(transfers) { tr ->
        // 已完成的条目 5 秒后自动收起
        if (tr.finished) {
            LaunchedEffect(tr.id) {
                delay(5000)
                LanManager.dismissTransfer(tr.id)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WearText(
                "${if (tr.sending) "发送" else "接收"}《${tr.fileName}》",
                fontSize = 10.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!tr.finished) {
                val frac = tr.fraction
                if (frac != null) {
                    TransferProgressRow(frac, tr.done, tr.total)
                } else {
                    WearText("传输中…", fontSize = 9.sp, color = LAN_ACCENT)
                }
            } else {
                WearText(
                    (if (tr.ok) "✓ " else "✗ ") + tr.message,
                    fontSize = 9.sp,
                    color = if (tr.ok) LAN_ACCENT else Color(0xFFFF8A80)
                )
            }
        }
    }
}

/** 传输进度条（细条 + 百分比 + 字节数） */
@Composable
private fun TransferProgressRow(fraction: Float, done: Long, total: Long) {    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x33FFFFFF))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LAN_ACCENT)
            )
        }
        WearText(
            "${(fraction * 100).toInt()}% · ${formatBytes(done)} / ${formatBytes(total)}",
            fontSize = 8.sp,
            color = Color.Gray
        )
    }
}

/** 字节数人类可读化 */
internal fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "未知大小"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}
