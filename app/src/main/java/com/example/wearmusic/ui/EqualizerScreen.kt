package com.example.wearmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusable
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Text
import com.example.wearmusic.BuildConfig
import com.example.wearmusic.EqPresets
import com.example.wearmusic.PlayerViewModel
import kotlin.math.abs

private val ACCENT = Color(0xFF34C7F4)

/**
 * 手表「音效与播放设置」页：
 * - 均衡器开关、预设、各频段增益手动调节（手指 −/+ 每格 3dB，旋转表冠进入「微调」每格 1dB）
 * - 播放模式、随机播放、播放速度、睡眠定时
 * - 支持旋转表冠滚动列表
 *
 * 频段默认预置 5 段（见 PlayerViewModel.init），因此未播放时也能直接看到并手动调节，
 * 不再只剩一个开关。点某个频段即可选中，随后「旋转表冠」以 1dB 步进微调该频段，点提示条退出微调。
 *
 * @param onOpenAlbumFlow 顶部「选曲」入口
 * @param active         仅当前可见页时抢占表冠焦点（横向翻页三页同在组合树，必须互斥）
 */
@Composable
fun AudioSettingsScreen(
    vm: PlayerViewModel,
    onOpenAlbumFlow: () -> Unit = {},
    active: Boolean = true,
) {
    val eqEnabled by vm.eqEnabled.collectAsState()
    val eqFreqs by vm.eqFreqs.collectAsState()
    val eqGains by vm.eqGains.collectAsState()
    val eqPreset by vm.eqPreset.collectAsState()
    val playMode by vm.playMode.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val speed by vm.speed.collectAsState()
    val sleepMinutes by vm.sleepMinutes.collectAsState()

    val listState = rememberScalingLazyListState()
    val listFr = rememberRotaryFocusRequester(active)
    // 微调模式：选中某频段后，表冠直接调增益（不再滚动列表）
    var selectedBand by remember { mutableStateOf<Int?>(null) }
    val knobFr = remember { FocusRequester() }
    LaunchedEffect(selectedBand, active) {
        if (selectedBand != null && active) {
            try { knobFr.requestFocus() } catch (_: Throwable) {}
        } else if (active) {
            try { listFr.requestFocus() } catch (_: Throwable) {}
        }
    }

    ScreenScaffold(scrollState = listState) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            ScalingLazyColumn(
                state = listState,
                contentPadding = padding,
                modifier = Modifier
                    .fillMaxSize()
                    .let { m -> if (active && selectedBand == null) m.rotaryScroll(listState, listFr) else m },
                flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(state = listState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Card(
                        onClick = onOpenAlbumFlow,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(contentColor = ACCENT)
                    ) {
                        Text("◀ 播放 · ▲ Cover Flow 选曲", fontSize = 11.sp)
                    }
                }

                item { Text("音效", fontSize = 14.sp) }

                // EQ 开关
                item {
                    Card(
                        onClick = { vm.setEqEnabled(!eqEnabled) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            contentColor = if (eqEnabled) ACCENT else Color.Gray
                        )
                    ) {
                        Text("均衡器", fontSize = 11.sp)
                        Text(if (eqEnabled) "已开启" else "已关闭", fontSize = 9.sp)
                    }
                }

                if (eqFreqs.isEmpty()) {
                    item {
                        Text(
                            "均衡器不可用",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // 预设
                    item { Text("预设", fontSize = 11.sp, color = Color.Gray) }
                    items(EqPresets.ALL.size) { i ->
                        val preset = EqPresets.ALL[i]
                        val selected = preset.name == eqPreset
                        Card(
                            onClick = { vm.applyPreset(preset) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (selected) {
                                CardDefaults.cardColors(contentColor = ACCENT)
                            } else {
                                CardDefaults.cardColors()
                            }
                        ) {
                            Text(preset.name, fontSize = 11.sp)
                        }
                    }

                    // 频段增益（手动调节）
                    item {
                        Text(
                            "频段增益 — 点频段后转表冠微调",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    items(eqFreqs.size) { i ->
                        val g = eqGains.getOrElse(i) { 0 }
                        val sel = selectedBand == i
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedBand = if (sel) null else i }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(
                                formatFreq(eqFreqs[i]),
                                fontSize = 10.sp,
                                color = if (sel) ACCENT else Color.Gray
                            )
                            LevelBar(gain = g, modifier = Modifier.fillMaxWidth(0.34f))
                            Text(
                                "%.1f".format(g / 100f),
                                fontSize = 11.sp,
                                color = if (sel) ACCENT else Color.White
                            )
                            CompactButton(onClick = { vm.setBandGain(i, g - 300) }) {
                                Text("−", fontSize = 12.sp)
                            }
                            CompactButton(onClick = { vm.setBandGain(i, g + 300) }) {
                                Text("+", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 播放设置
                item { Text("播放", fontSize = 11.sp, color = Color.Gray) }
                item {
                    Card(
                        onClick = vm::cyclePlayMode,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors()
                    ) {
                        Text("播放模式", fontSize = 11.sp)
                        Text(playMode.label, fontSize = 9.sp)
                    }
                }
                item {
                    Card(
                        onClick = vm::toggleShuffle,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            contentColor = if (shuffle) ACCENT else Color.Gray
                        )
                    ) {
                        Text("随机播放", fontSize = 11.sp)
                        Text(if (shuffle) "已开启" else "已关闭", fontSize = 9.sp)
                    }
                }
                item {
                    Card(
                        onClick = { vm.setSpeed(nextPlaybackSpeed(speed)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors()
                    ) {
                        Text("播放速度", fontSize = 11.sp)
                        Text("${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x", fontSize = 9.sp)
                    }
                }

                // 睡眠定时
                item { Text("睡眠定时", fontSize = 11.sp, color = Color.Gray) }
                items(listOf(0, 15, 30, 60).size) { i ->
                    val minutes = listOf(0, 15, 30, 60)[i]
                    val selected = sleepMinutes == minutes
                    Card(
                        onClick = { vm.setSleepTimer(minutes) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (selected) {
                            CardDefaults.cardColors(contentColor = ACCENT)
                        } else {
                            CardDefaults.cardColors()
                        }
                    ) {
                        Text(
                            if (minutes == 0) "关闭" else "$minutes 分钟",
                            fontSize = 11.sp
                        )
                    }
                }

                // 版本号（用于确认当前安装的构建版本）
                item {
                    Text(
                        "WearMusic v${BuildConfig.VERSION_NAME}",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 微调模式覆盖提示条：选中频段后，表冠直接调增益，点此退出
            if (active && selectedBand != null) {
                val bandName = eqFreqs.getOrNull(selectedBand!!)?.let { formatFreq(it) } ?: ""
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(Color(0xCC06070A), RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                        .focusRequester(knobFr)
                        .focusable()
                        .onRotaryScrollEvent { event ->
                            val dir = if (event.verticalScrollPixels >= 0) 1 else -1
                            selectedBand?.let { b ->
                                val cur = vm.eqGains.value.getOrElse(b) { 0 }
                                vm.setBandGain(b, cur + dir * 100)
                            }
                            true
                        }
                        .clickable { selectedBand = null }
                ) {
                    Text(
                        "🎚 表冠微调 $bandName · 点此退出",
                        color = ACCENT,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

/** 频段增益电平条：增益映射到 -15..15dB，正增益向右(蓝)、负增益向左(红)。 */
@Composable
private fun LevelBar(gain: Int, modifier: Modifier = Modifier) {
    val frac = (gain / 1500f).coerceIn(-1f, 1f)
    Box(
        modifier
            .height(4.dp)
            .background(Color(0x33FFFFFF), RoundedCornerShape(2.dp))
    ) {
        val w = abs(frac) * 0.5f
        Box(
            Modifier
                .fillMaxWidth(w)
                .height(4.dp)
                .align(if (frac >= 0f) Alignment.CenterEnd else Alignment.CenterStart)
                .background(
                    if (frac >= 0f) ACCENT else Color(0xFFFF5A7A),
                    RoundedCornerShape(2.dp)
                )
        )
    }
}

private fun formatFreq(hz: Int): String =
    if (hz >= 1000) "${hz / 1000}k" else "$hz"

private fun nextPlaybackSpeed(cur: Float): Float {
    val steps = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    return steps.firstOrNull { it > cur + 0.01f } ?: steps.first()
}
