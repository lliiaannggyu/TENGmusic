package com.tengwear.tengmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
 * 局域网「一起听」页：
 *  - 发现同一 Wi-Fi 下运行本 App 的设备，把**当前播放的歌曲+歌词**直接推过去
 *  - 对方无需在本机拥有这首歌：字节走局域网落到缓存目录，退出即删（不留残余）
 *  - 对端推送过来时自动弹出询问卡：接受 → 收歌 → 直接起播；拒绝 → 不留文件
 *  - 自己在播推送歌时，显示「退出一起听」（退出即清缓存并停止）
 */
@Composable
fun ListenTogetherScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val peers by LanManager.peers.collectAsState()
    val transfers by LanManager.transfers.collectAsState()
    val sharedMode by vm.sharedMode.collectAsState()
    val currentTrack by vm.currentTrack.collectAsState()
    val incomingOffer by LanManager.pendingListenOffer.collectAsState()
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester()

    var selectedPeerId by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    // 全局事件：推送结果的接收侧起播 / 失败提示
    LaunchedEffect(Unit) {
        LanManager.events.collect { ev ->
            when (ev) {
                is LanEvent.Done -> {
                    if (ev.kind == LanProtocol.KIND_LISTEN && !ev.sending) {
                        if (ev.ok) {
                            val file = LanManager.takeStagedListenFile(ev.transferId)
                            if (file != null) {
                                vm.playSharedSong(file, ev.title, ev.artist, ev.lyric)
                                statusMsg = null
                            } else {
                                statusMsg = "缓存文件缺失"
                            }
                        } else {
                            statusMsg = "接收失败：${ev.message}"
                        }
                    } else if (!ev.ok) {
                        statusMsg = "发送失败：${ev.message}"
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

                // ========== 正在一起听（播放推送歌）==========
                if (sharedMode) {
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = LT_CARD_SELECTED,
                                contentColor = Color.White,
                                titleColor = LT_ACCENT,
                                subtitleColor = Color.Gray,
                            ),
                        ) {
                            WearText("正在一起听", fontSize = 11.sp, color = LT_ACCENT)
                            currentTrack?.let { t ->
                                WearText(
                                    "《${t.title}》",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (t.artist.isNotBlank()) {
                                    WearText(t.artist, fontSize = 9.sp, color = Color.Gray, maxLines = 1)
                                }
                            }
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
                            "退出后不保留缓存文件",
                            fontSize = 8.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ========== 收到的推送（待决定）==========
                val offer = incomingOffer
                if (offer != null) {
                    val receiving = transfers.any { it.id == offer.transferId && !it.finished }
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = LT_CARD_SELECTED,
                                contentColor = Color.White,
                                titleColor = LT_ACCENT,
                                subtitleColor = Color.Gray,
                            ),
                        ) {
                            WearText("一起听邀请", fontSize = 11.sp, color = LT_ACCENT)
                            WearText(
                                "${offer.peer.name} 想和你一起听：",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                            WearText(
                                "《${offer.title}》",
                                fontSize = 10.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (offer.artist.isNotBlank()) {
                                WearText(offer.artist, fontSize = 8.sp, color = Color.Gray, maxLines = 1)
                            }
                            if (receiving) {
                                WearText("正在接收…", fontSize = 10.sp, color = LT_ACCENT)
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { LanManager.respondOffer(offer.transferId, true) }
                                    ) { WearText("接受", fontSize = 10.sp) }
                                    Button(
                                        onClick = {
                                            LanManager.respondOffer(offer.transferId, false)
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

                // ========== 推送当前歌曲（非一起听状态）==========
                if (!sharedMode) {
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

                    item { ListHeader { WearText("推送当前歌曲", fontSize = 11.sp) } }
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val t = currentTrack
                            WearText(
                                if (t != null) "将推送《${t.title}》" else "请先播放一首歌",
                                fontSize = 9.sp,
                                color = if (t != null) Color.White else Color.Gray,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Button(
                                onClick = {
                                    val peer = peers.find { it.id == selectedPeerId }
                                    val track = currentTrack
                                    if (peer != null && track != null) {
                                        LanManager.pushListen(
                                            peer, track, vm.currentLyricTextForPush()
                                        )
                                        statusMsg = "已推送给 ${peer.name}，等待对方接受…"
                                    }
                                },
                                enabled = selectedPeerId != null && currentTrack != null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                WearText("推送并一起听", fontSize = 11.sp)
                            }
                            WearText(
                                "歌曲与歌词一起推送\n对方无需拥有这首歌",
                                fontSize = 8.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
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
                            WearText(msg, fontSize = 9.sp, color = LT_ACCENT, textAlign = TextAlign.Center)
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
