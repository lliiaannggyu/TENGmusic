package com.tengwear.tengmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.foundation.focusable
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SliderDefaults
import androidx.wear.compose.material3.Text
import com.tengwear.tengmusic.BuildConfig
import com.tengwear.tengmusic.EqPresets
import com.tengwear.tengmusic.PlayMode
import com.tengwear.tengmusic.PlayerViewModel
import kotlin.math.abs
import kotlin.math.ln

private val ACCENT = Color(0xFF34C7F4)

/** 播放速度档位（RadioButton 单选列表与循环切换共用） */
private val SPEED_STEPS = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/** 歌词自定义配色预设（0xAARRGGBB）：白 / 青 / 黄 / 绿 / 粉 / 橙 / 紫 */
private val LYRIC_PALETTE = listOf(
    0xFFFFFFFF.toInt(),
    0xFF34C7F4.toInt(),
    0xFFFFD23F.toInt(),
    0xFF4BE08A.toInt(),
    0xFFFF6BA6.toInt(),
    0xFFFF9F43.toInt(),
    0xFFB07CFF.toInt(),
)

/**
 * 手表「音效与播放设置」页（Wear M3E 列表：ScalingLazyColumn + ListHeader +
 * RadioButton / SwitchButton / Slider 标准列表项）：
 * - 播放：播放模式、随机播放、播放速度（RadioButton / SwitchButton）
 * - 音效：均衡器开关、预设、各频段增益手动调节（手指 −/+ 每格 3dB，旋转表冠进入「微调」每格 1dB）
 * - 睡眠定时
 * - 播放器外观：进度环半径 / 粗细、背景模糊 / 明度、歌词颜色（跟随封面或自定义色板）
 * - 关于：UI 版本信息、开源许可
 * - 支持旋转表冠滚动列表
 *
 * 排序原则：按使用频率从高到低——播放操作最常用置顶，音效次之，外观与关于收底。
 *
 * 频段默认预置 5 段（见 PlayerViewModel.init），因此未播放时也能直接看到并手动调节，
 * 不再只剩一个开关。点某个频段即可选中，随后「旋转表冠」以 1dB 步进微调该频段，点提示条退出微调。
 *
 * @param onOpenAlbumFlow 预留入口（专辑流由播放页上滑唤起）
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
    val blurRadius by vm.blurRadius.collectAsState()
    val dimAlpha by vm.dimAlpha.collectAsState()
    val lyricColorMode by vm.lyricColorMode.collectAsState()
    val lyricCustomColor by vm.lyricCustomColor.collectAsState()
    val ringRadiusFrac by vm.ringRadiusFrac.collectAsState()
    val ringStroke by vm.ringStroke.collectAsState()

    val listState = rememberScalingLazyListState()
    val listFr = rememberRotaryFocusRequester(active)
    // 微调模式：选中某频段后，表冠直接调增益（不再滚动列表）
    var selectedBand by remember { mutableStateOf<Int?>(null) }
    // 频段调节区是否展开（默认折叠，点标题展开预设 + 手动增益）
    var eqExpanded by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
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
                // ========== 1. 播放（最高频操作，置顶）==========
                item { ListHeader { Text("播放") } }

                item {
                    Text(
                        "播放模式",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 2.dp),
                    )
                }
                PlayMode.entries.forEach { mode ->
                    item {
                        RadioButton(
                            selected = playMode == mode,
                            onSelect = { vm.setPlayMode(mode) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(mode.label, fontSize = 11.sp) },
                        )
                    }
                }

                item {
                    SwitchButton(
                        checked = shuffle,
                        onCheckedChange = { vm.toggleShuffle() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("随机播放", fontSize = 11.sp) },
                        secondaryLabel = {
                            Text(if (shuffle) "已开启" else "已关闭", fontSize = 9.sp)
                        },
                    )
                }

                item {
                    Text(
                        "播放速度",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 2.dp),
                    )
                }
                SPEED_STEPS.forEach { s ->
                    item {
                        RadioButton(
                            selected = abs(speed - s) < 0.001f,
                            onSelect = { vm.setSpeed(s) },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    "${"%.2f".format(s).trimEnd('0').trimEnd('.')}x",
                                    fontSize = 11.sp,
                                )
                            },
                        )
                    }
                }

                // ========== 2. 音效 ==========
                item { ListHeader { Text("音效") } }

                // EQ 开关（M3E SwitchButton）
                item {
                    SwitchButton(
                        checked = eqEnabled,
                        onCheckedChange = { vm.setEqEnabled(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("均衡器", fontSize = 11.sp) },
                        secondaryLabel = {
                            Text(if (eqEnabled) "已开启" else "已关闭", fontSize = 9.sp)
                        },
                    )
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
                    // 频率响应曲线（随各频段增益实时变化）
                    item {
                        EqCurve(
                            freqs = eqFreqs,
                            gains = eqGains,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // 频段调节（可折叠）：标题卡显示当前预设，点开才显示预设列表 + 手动增益
                    item {
                        Card(
                            onClick = { eqExpanded = !eqExpanded },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors()
                        ) {
                            Text(
                                if (eqExpanded) "频段调节 ▲" else "频段调节 ▼",
                                fontSize = 11.sp,
                                color = ACCENT
                            )
                            Text(eqPreset, fontSize = 9.sp)
                        }
                    }

                    if (eqExpanded) {
                        // 预设（M3E RadioButton 单选）
                        item { ListHeader { Text("预设") } }
                        items(EqPresets.ALL.size) { i ->
                            val preset = EqPresets.ALL[i]
                            val selected = preset.name == eqPreset
                            RadioButton(
                                selected = selected,
                                onSelect = { vm.applyPreset(preset) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(preset.name, fontSize = 11.sp) },
                            )
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
                            // 频段增益改用 M3E Slider（组件库），点频段后仍可用表冠微调
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedBand = if (sel) null else i }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "${formatFreq(eqFreqs[i])}   ${"%.1f".format(g / 100f)} dB",
                                    fontSize = 10.sp,
                                    color = if (sel) ACCENT else Color.Gray
                                )
                                Slider(
                                    value = (g / 100f).coerceIn(-15f, 15f),
                                    onValueChange = { vm.setBandGain(i, (it * 100f).toInt()) },
                                    // steps 必须 > 0：M3E Slider 的 ±按钮步长 = 量程/(steps+1)，
                                    // steps=0 时步长=整个量程，点一下直接跳到最小/最大
                                    steps = 300,
                                    valueRange = -15f..15f,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                // 睡眠定时（无极调节进度条：0–120 分钟连续拖动）
                item { ListHeader { Text("睡眠定时") } }
                item {
                    val mins = sleepMinutes
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (mins <= 0) "关闭" else "$mins 分钟",
                            fontSize = 12.sp,
                            color = ACCENT,
                            textAlign = TextAlign.Center
                        )
                        Slider(
                            value = mins.toFloat().coerceIn(0f, 120f),
                            onValueChange = { vm.setSleepTimer(it.roundToInt()) },
                            steps = 120,
                            valueRange = 0f..120f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // ========== 4. 播放器外观（进度环 / 背景 / 歌词颜色）==========
                item { ListHeader { Text("播放器外观") } }
                // 进度环半径（占屏幕短边比例）
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("进度环半径 ${(ringRadiusFrac * 100).toInt()}%", fontSize = 11.sp, color = ACCENT)
                        Slider(
                            value = ringRadiusFrac.coerceIn(0.30f, 0.50f),
                            onValueChange = { vm.setRingRadiusFrac(it) },
                            steps = 20,
                            valueRange = 0.30f..0.50f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // 说明物理上限，避免用户误以为还能更大
                        Text(
                            "50% 时圆环直径 = 屏幕短边（顶到圆屏边缘，为物理上限）",
                            fontSize = 8.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // 进度环粗细（dp）
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("进度环粗细 ${ringStroke.toInt()}dp", fontSize = 11.sp, color = ACCENT)
                        Slider(
                            value = ringStroke,
                            onValueChange = { vm.setRingStroke(it) },
                            steps = 10,
                            valueRange = 2f..12f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // 播放器背景（专辑封面作背景）：模糊强度 + 明度（暗化）可调
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("背景模糊 ${blurRadius.toInt()}dp", fontSize = 11.sp, color = ACCENT)
                        Slider(
                            value = blurRadius,
                            onValueChange = { vm.setBlurRadius(it) },
                            steps = 40,
                            valueRange = 0f..40f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val brightness = (100f - dimAlpha / 0.95f * 100f).coerceIn(0f, 100f)
                        Text("背景明度 ${brightness.toInt()}", fontSize = 11.sp, color = ACCENT)
                        Slider(
                            value = brightness,
                            onValueChange = { vm.setBackgroundBrightness(it) },
                            steps = 20,
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // 歌词颜色：跟随专辑封面主色，或用户从预设色板自定义
                item { ListHeader { Text("歌词颜色") } }
                item {
                    RadioButton(
                        selected = lyricColorMode == 0,
                        onSelect = { vm.setLyricColorMode(0) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("跟随专辑封面", fontSize = 11.sp) },
                        secondaryLabel = {
                            Text(if (lyricColorMode == 0) "当前使用" else "点按使用", fontSize = 9.sp)
                        },
                    )
                }
                item {
                    RadioButton(
                        selected = lyricColorMode == 1,
                        onSelect = { vm.setLyricColorMode(1) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("自定义颜色", fontSize = 11.sp) },
                        secondaryLabel = { Text("从下方色板选择", fontSize = 9.sp) },
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LYRIC_PALETTE.forEach { c ->
                            val selected = lyricColorMode == 1 && lyricCustomColor == c
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(Color(c), CircleShape)
                                    .then(
                                        if (selected) {
                                            Modifier.border(2.dp, Color.White, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { vm.setLyricCustomColor(c) }
                            )
                        }
                    }
                }

                // ========== 5. 关于 ==========
                item { ListHeader { Text("关于") } }

                // 播放器主题：仅保留 M3E 一套 UI（经典主题已移除），无需再切换
                item {
                    Card(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(contentColor = ACCENT),
                    ) {
                        Text("播放器 UI", fontSize = 11.sp)
                        Text("M3E（Material 3 Expressive）", fontSize = 9.sp)
                    }
                }

                // 开源许可（应用内致谢：列出用到的开源项目及地址）
                item {
                    Card(
                        onClick = { showLicenses = !showLicenses },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(contentColor = ACCENT),
                    ) {
                        Text(if (showLicenses) "用到的开源项目 ▲" else "用到的开源项目 ▼", fontSize = 11.sp)
                        Text("共 ${OpenSourceProjects.ALL.size} 项", fontSize = 9.sp)
                    }
                }
                if (showLicenses) {
                    items(OpenSourceProjects.ALL.size) { i ->
                        val p = OpenSourceProjects.ALL[i]
                        Card(
                            onClick = { },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(),
                        ) {
                            Text(p.name, fontSize = 9.sp, maxLines = 2)
                            Text(p.license, fontSize = 8.sp, color = ACCENT)
                            Text(p.url, fontSize = 7.sp, color = Color.Gray, maxLines = 1)
                        }
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

/**
 * 频率响应曲线：横轴为频段中心频率（对数轴 20Hz–20kHz），纵轴为增益（dB，范围 ±15）。
 * 把每个频段的增益连成折线并在数据点画圆点，直观反映当前均衡器形状；增益变化时实时重绘。
 */
@Composable
private fun EqCurve(freqs: List<Int>, gains: List<Int>, modifier: Modifier = Modifier) {
    val gMin = -15f
    val gMax = 15f
    val fMin = 20f
    val fMax = 20000f
    Canvas(modifier = modifier.height(64.dp).padding(4.dp)) {
        val w = size.width
        val h = size.height
        val pad = 4.dp.toPx()
        val dbOf: (Int) -> Float = { it / 100f }
        val xOf: (Int) -> Float = { f ->
            val t = (ln(f.toFloat()) - ln(fMin)) / (ln(fMax) - ln(fMin))
            pad + t * (w - 2 * pad)
        }
        val yOf: (Float) -> Float = { db ->
            val t = (db - gMin) / (gMax - gMin)
            h - pad - t * (h - 2 * pad)
        }
        // 0 dB 基准线
        drawLine(
            color = Color(0x33FFFFFF),
            start = Offset(pad, yOf(0f)),
            end = Offset(w - pad, yOf(0f)),
            strokeWidth = 1.dp.toPx(),
        )
        // 响应曲线
        val path = Path()
        freqs.forEachIndexed { i, f ->
            val px = xOf(f)
            val py = yOf(dbOf(gains.getOrElse(i) { 0 }))
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(
            path = path,
            color = ACCENT,
            style = Stroke(width = 2.dp.toPx()),
        )
        // 数据点
        freqs.forEachIndexed { i, f ->
            drawCircle(
                color = ACCENT,
                radius = 3.dp.toPx(),
                center = Offset(xOf(f), yOf(dbOf(gains.getOrElse(i) { 0 }))),
            )
        }
    }
}

private fun formatFreq(hz: Int): String =
    if (hz >= 1000) "${hz / 1000}k" else "$hz"

private fun nextPlaybackSpeed(cur: Float): Float {
    return SPEED_STEPS.firstOrNull { it > cur + 0.01f } ?: SPEED_STEPS.first()
}
