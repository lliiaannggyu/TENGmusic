package com.example.wearmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Text
import com.example.wearmusic.EqPresets
import com.example.wearmusic.PlayerViewModel

private val ACCENT = Color(0xFF34C7F4)

/**
 * 手表「音效与播放设置」页：
 * - 均衡器开关、预设、各频段增益调节（每段 ±300 millibel = ±3dB）
 * - 播放模式（列表顺序/列表随机/单曲循环）、随机播放、播放速度、睡眠定时
 * - 支持旋转表冠滚动
 *
 * @param onOpenAlbumFlow 顶部「选曲」入口：列表会吃掉垂直手势，这里补一个点击入口进入专辑流
 */
@Composable
fun AudioSettingsScreen(vm: PlayerViewModel, onOpenAlbumFlow: () -> Unit = {}) {
    val eqEnabled by vm.eqEnabled.collectAsState()
    val eqFreqs by vm.eqFreqs.collectAsState()
    val eqGains by vm.eqGains.collectAsState()
    val eqPreset by vm.eqPreset.collectAsState()
    val playMode by vm.playMode.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val speed by vm.speed.collectAsState()
    val sleepMinutes by vm.sleepMinutes.collectAsState()

    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester()

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .rotaryScroll(listState, focusRequester),
        flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(state = listState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Chip(
                onClick = onOpenAlbumFlow,
                label = { Text("◀ 播放 · ▲ Cover Flow 选曲", fontSize = 11.sp) },
                colors = ChipDefaults.chipColors(contentColor = ACCENT)
            )
        }

        item { Text("音效", fontSize = 14.sp) }

        // EQ 开关
        item {
            Chip(
                onClick = { vm.setEqEnabled(!eqEnabled) },
                label = { Text("均衡器", fontSize = 11.sp) },
                secondaryLabel = { Text(if (eqEnabled) "已开启" else "已关闭", fontSize = 9.sp) },
                colors = ChipDefaults.chipColors(
                    contentColor = if (eqEnabled) ACCENT else Color.Gray
                )
            )
        }

        if (eqFreqs.isEmpty()) {
            item {
                Text(
                    "开始播放后\n均衡器自动接管音频",
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
                Chip(
                    onClick = { vm.applyPreset(preset) },
                    label = { Text(preset.name, fontSize = 11.sp) },
                    colors = if (selected) {
                        ChipDefaults.chipColors(contentColor = ACCENT)
                    } else {
                        ChipDefaults.chipColors()
                    }
                )
            }

            // 频段增益
            item { Text("频段增益 (dB)", fontSize = 11.sp, color = Color.Gray) }
            items(eqFreqs.size) { i ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(formatFreq(eqFreqs[i]), fontSize = 10.sp, color = Color.Gray)
                    CompactButton(onClick = { vm.setBandGain(i, eqGains[i] - 300) }) {
                        Text("−", fontSize = 12.sp)
                    }
                    Text(
                        "${eqGains[i] / 100}",
                        fontSize = 11.sp,
                        color = if (eqEnabled) ACCENT else Color.Gray
                    )
                    CompactButton(onClick = { vm.setBandGain(i, eqGains[i] + 300) }) {
                        Text("+", fontSize = 12.sp)
                    }
                }
            }
        }

        // 播放设置
        item { Text("播放", fontSize = 11.sp, color = Color.Gray) }
        item {
            Chip(
                onClick = vm::cyclePlayMode,
                label = { Text("播放模式", fontSize = 11.sp) },
                secondaryLabel = { Text(playMode.label, fontSize = 9.sp) }
            )
        }
        item {
            Chip(
                onClick = vm::toggleShuffle,
                label = { Text("随机播放", fontSize = 11.sp) },
                secondaryLabel = { Text(if (shuffle) "已开启" else "已关闭", fontSize = 9.sp) },
                colors = ChipDefaults.chipColors(
                    contentColor = if (shuffle) ACCENT else Color.Gray
                )
            )
        }
        item {
            Chip(
                onClick = { vm.setSpeed(nextPlaybackSpeed(speed)) },
                label = { Text("播放速度", fontSize = 11.sp) },
                secondaryLabel = {
                    Text("${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x", fontSize = 9.sp)
                }
            )
        }

        // 睡眠定时
        item { Text("睡眠定时", fontSize = 11.sp, color = Color.Gray) }
        items(listOf(0, 15, 30, 60).size) { i ->
            val minutes = listOf(0, 15, 30, 60)[i]
            val selected = sleepMinutes == minutes
            Chip(
                onClick = { vm.setSleepTimer(minutes) },
                label = {
                    Text(
                        if (minutes == 0) "关闭" else "$minutes 分钟",
                        fontSize = 11.sp
                    )
                },
                colors = if (selected) {
                    ChipDefaults.chipColors(contentColor = ACCENT)
                } else {
                    ChipDefaults.chipColors()
                }
            )
        }
    }
}

private fun formatFreq(hz: Int): String =
    if (hz >= 1000) "${hz / 1000}k" else "$hz"

private fun nextPlaybackSpeed(cur: Float): Float {
    val steps = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    return steps.firstOrNull { it > cur + 0.01f } ?: steps.first()
}
