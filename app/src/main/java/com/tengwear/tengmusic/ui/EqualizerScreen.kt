package com.tengwear.tengmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.foundation.focusable
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.RadioButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SliderDefaults
import androidx.wear.compose.material3.Text
import com.tengwear.tengmusic.BuildConfig
import com.tengwear.tengmusic.EqPresets
import com.tengwear.tengmusic.PlayerViewModel
import com.tengwear.tengmusic.SettingsRepository
import kotlin.math.abs
import kotlin.math.ln

/** 播放速度档位（RadioButton 单选列表共用） */
private val SPEED_STEPS = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/**
 * 歌词自定义配色预设（0xAARRGGBB）—— Material 3 官方 baseline 色阶（tonal palette）。
 *
 * 这就是 M3 官方「种子色 → 色调阶（tone）」的完整色阶：以 Primary #6750A4 为种子，
 * 按 tone 10/20/30/40/50/60/70/80/90/95 逐级提亮，另有 Secondary / Tertiary / Error /
 * Neutral 的 tone 40/80 阶。全部取自 M3 官方 baseline scheme，与 M3E 组件库视觉一致。
 * 色阶之外另提供「自定义」入口：直接填写十六进制色号（#RRGGBB / #AARRGGBB / RRGGBB）。
 */
private val TONAL_PALETTE = listOf(
    0xFFFFFFFF.toInt(), // 纯白
    // —— Primary 色阶（种子 #6750A4，tone 10 → 95）——
    0xFF21005D.toInt(), // P10
    0xFF381E72.toInt(), // P20
    0xFF4F378B.toInt(), // P30
    0xFF6750A4.toInt(), // P40（基线主色）
    0xFF7F67BE.toInt(), // P50
    0xFF9A82DB.toInt(), // P60
    0xFFB69DF8.toInt(), // P70
    0xFFD0BCFF.toInt(), // P80
    0xFFEADDFF.toInt(), // P90
    0xFFF6EDFF.toInt(), // P95
    // —— Secondary / Tertiary / Error / Neutral（tone 40 / 80）——
    0xFF625B71.toInt(), // S40
    0xFFCCC2DC.toInt(), // S80
    0xFF7D5260.toInt(), // T40
    0xFFEFB8C8.toInt(), // T80
    0xFFB3261E.toInt(), // E40
    0xFFF2B8B5.toInt(), // E80
    0xFFE6E0E9.toInt(), // N90
)

/** 列表容器底色（深色底，与 M3E dark scheme 的 surface 一致） */
private val CARD_BG = Color(0xF20B0B0E)

/**
 * 手表「音效与播放设置」页（Wear M3E 列表：ScalingLazyColumn + ListHeader +
 * RadioButton / SwitchButton / Slider / Card 标准列表项）：
 * - 播放：随机播放、播放速度（SwitchButton / RadioButton）
 * - 音效：均衡器开关、频率响应曲线、预设单选（内置 + 设备预设）、
 *   竖条频段调节（形态移植自 AndroidEqualizer 的 EqualizerView）
 * - 睡眠定时
 * - 歌词：字号、颜色（M3 官方 tonal 色阶 / 自定义十六进制色号）
 * - 歌词切换动画：多种切换动画可选，带实时预览
 * - 局域网：传歌（超阈值先压缩）、一起听（推当前歌+歌词，退出不留残余）、压缩阈值
 * - 工具：封面工具（批量写入专辑封面）
 * - 关于：UI 版本信息、开源许可
 * - 支持旋转表冠滚动列表
 *
 * 列表组件（Card / RadioButton / SwitchButton / Slider）的颜色全部由主题强调色
 * （跟随歌词色：专辑主色或用户自定义色）实时衍生，保证「列表组件颜色跟着主题色变」。
 *
 * 频段默认预置 5 段（见 PlayerViewModel.init），因此未播放时也能直接看到并手动调节，
 * 不再只剩一个开关。点某个频段即可选中，随后旋转表冠以 1dB 步进微调该频段。
 *
 * @param onOpenCoverTool 打开封面工具（给音频文件写入专辑封面）
 * @param onOpenLanTransfer 打开局域网传歌页（同一 Wi-Fi 下互传歌曲文件）
 * @param onOpenListenTogether 打开一起听页（把当前歌曲+歌词推给附近设备）
 * @param active          仅当前可见页时抢占表冠焦点（横向翻页三页同在组合树，必须互斥）
 */
@Composable
fun AudioSettingsScreen(
    vm: PlayerViewModel,
    onOpenAlbumFlow: () -> Unit = {},
    onOpenCoverTool: () -> Unit = {},
    onOpenLanTransfer: () -> Unit = {},
    onOpenListenTogether: () -> Unit = {},
    active: Boolean = true,
) {
    val eqEnabled by vm.eqEnabled.collectAsState()
    val eqFreqs by vm.eqFreqs.collectAsState()
    val eqGains by vm.eqGains.collectAsState()
    val eqPreset by vm.eqPreset.collectAsState()
    val eqDevicePresets by vm.eqDevicePresets.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val speed by vm.speed.collectAsState()
    val sleepMinutes by vm.sleepMinutes.collectAsState()
    val blurRadius by vm.blurRadius.collectAsState()
    val dimAlpha by vm.dimAlpha.collectAsState()
    val lyricColorMode by vm.lyricColorMode.collectAsState()
    val lyricCustomColor by vm.lyricCustomColor.collectAsState()
    val lyricFontSize by vm.lyricFontSize.collectAsState()
    val ringRadiusFrac by vm.ringRadiusFrac.collectAsState()
    val ringStroke by vm.ringStroke.collectAsState()
    // 进度条样式（0 = 圆环，1 = 波浪），设置页可切换
    val progressStyle by vm.progressStyle.collectAsState()
    // 设置页强调色跟随歌词主题色（专辑主色或用户自定义色）
    val lyricColor by vm.lyricColor.collectAsState()
    val lyricTransition by vm.lyricTransition.collectAsState()
    val accent = Color(lyricColor)

    val listState = rememberScalingLazyListState()
    val listFr = rememberRotaryFocusRequester(active)
    // 微调模式：选中某频段后，表冠直接调增益（不再滚动列表）
    var selectedBand by remember { mutableStateOf<Int?>(null) }
    // 频段调节区是否展开（默认折叠，点标题展开预设 + 竖条调节）
    var eqExpanded by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    // 设置页各分组默认展开/折叠状态（常用分组默认展开，其余默认折叠，点标题展开）
    var secPlay by remember { mutableStateOf(true) }
    var secSound by remember { mutableStateOf(true) }
    var secSleep by remember { mutableStateOf(false) }
    var secAppearance by remember { mutableStateOf(false) }
    var secLyric by remember { mutableStateOf(false) }
    var secLyricAnim by remember { mutableStateOf(false) }
    var secLan by remember { mutableStateOf(false) }
    var secTool by remember { mutableStateOf(true) }
    var secAbout by remember { mutableStateOf(false) }
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
                // ========== 1. 播放（最高频操作，置顶，默认展开）==========
                collapsibleSection("播放", secPlay, { secPlay = !secPlay }, accent) {
                    item {
                        SwitchButton(
                            checked = shuffle,
                            onCheckedChange = { vm.toggleShuffle() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("随机播放", fontSize = 11.sp) },
                            secondaryLabel = {
                                Text(if (shuffle) "已开启" else "已关闭", fontSize = 9.sp)
                            },
                            colors = accentSwitchColors(accent),
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
                                colors = accentRadioColors(accent),
                            )
                        }
                    }
                }

                // ========== 2. 音效（均衡器，默认展开）==========
                collapsibleSection("音效", secSound, { secSound = !secSound }, accent) {
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
                            colors = accentSwitchColors(accent),
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
                                accent = accent,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // 频段调节（可折叠）：标题卡显示当前预设，点开才显示预设列表 + 竖条调节
                        item {
                            Card(
                                onClick = { eqExpanded = !eqExpanded },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(percent = 50),
                                colors = accentCardColors(accent),
                            ) {
                                Text(
                                    if (eqExpanded) "频段调节 ▲" else "频段调节 ▼",
                                    fontSize = 11.sp,
                                    color = accent
                                )
                                Text(eqPreset, fontSize = 9.sp)
                            }
                        }

                        if (eqExpanded) {
                            // 内置预设（M3E RadioButton 单选）
                            item { ListHeader { Text("预设") } }
                            items(EqPresets.ALL.size) { i ->
                                val preset = EqPresets.ALL[i]
                                val selected = preset.name == eqPreset
                                RadioButton(
                                    selected = selected,
                                    onSelect = { vm.applyPreset(preset) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(preset.name, fontSize = 11.sp) },
                                    colors = accentRadioColors(accent),
                                )
                            }

                            // 设备内置预设（系统 Equalizer 自带，移植自 AndroidEqualizer）
                            if (eqDevicePresets.isNotEmpty()) {
                                item { ListHeader { Text("设备预设") } }
                                items(eqDevicePresets.size) { i ->
                                    val name = eqDevicePresets[i]
                                    RadioButton(
                                        selected = name == eqPreset,
                                        onSelect = { vm.applyDevicePreset(i) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(name, fontSize = 11.sp) },
                                        colors = accentRadioColors(accent),
                                    )
                                }
                            }

                            // 重置为平直（M3E CompactButton）
                            item {
                                CompactButton(
                                    onClick = { vm.applyPreset(EqPresets.ALL.first()) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("重置为平直", fontSize = 11.sp)
                                }
                            }

                            // 竖条频段调节（形态移植自 AndroidEqualizer 的 EqualizerView：
                            // 每频段一根竖向滑条，顶部 dB 值、底部频率，0dB 居中刻度线）
                            item { ListHeader { Text("频段") } }
                            item {
                                EqBandBars(
                                    freqs = eqFreqs,
                                    gains = eqGains,
                                    selected = selectedBand,
                                    accent = accent,
                                    onChange = { band, db ->
                                        selectedBand = band
                                        vm.setBandGain(band, (db * 100f).toInt())
                                    },
                                )
                            }
                        }
                    }
                }

                // ========== 3. 睡眠定时（默认折叠）==========
                collapsibleSection("睡眠定时", secSleep, { secSleep = !secSleep }, accent) {
                    item {
                        val mins = sleepMinutes
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (mins <= 0) "关闭" else "$mins 分钟",
                                fontSize = 12.sp,
                                color = accent,
                                textAlign = TextAlign.Center
                            )
                            Slider(
                                value = mins.toFloat().coerceIn(0f, 120f),
                                onValueChange = { vm.setSleepTimer(it.roundToInt()) },
                                steps = 120,
                                valueRange = 0f..120f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = accentSliderColors(accent),
                            )
                        }
                    }
                }

                // ========== 4. 播放器外观（进度条样式 / 进度环 / 背景，默认折叠）==========
                collapsibleSection("播放器外观", secAppearance, { secAppearance = !secAppearance }, accent) {
                // 进度条样式：圆环 / 波浪圆环（M3E 官方 CircularWavyProgressIndicator 同款）二选一
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("进度条样式", fontSize = 11.sp, color = accent)
                        // 圆环（M3E RadioButton：onSelect + label 槽位）
                        RadioButton(
                            selected = progressStyle == SettingsRepository.PROGRESS_STYLE_RING,
                            onSelect = {
                                vm.setProgressStyle(SettingsRepository.PROGRESS_STYLE_RING)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Row(
                                    Modifier.fillMaxWidth().padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "圆环",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                    CircularProgressIndicator(
                                        progress = { 0.6f },
                                        modifier = Modifier.size(26.dp),
                                        colors = ProgressIndicatorDefaults.colors(
                                            indicatorColor = accent,
                                            trackColor = Color(0x22FFFFFF),
                                        ),
                                        strokeWidth = 3.dp,
                                    )
                                }
                            },
                            colors = accentRadioColors(accent),
                        )
                        // 波浪圆环（M3E 官方 CircularWavyProgressIndicator 构件本体）
                        RadioButton(
                            selected = progressStyle == SettingsRepository.PROGRESS_STYLE_WAVY,
                            onSelect = {
                                vm.setProgressStyle(SettingsRepository.PROGRESS_STYLE_WAVY)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Row(
                                    Modifier.fillMaxWidth().padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "波浪圆环 · M3E 官方",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                    CircularWavyProgressIndicator(
                                        progress = { 0.65f },
                                        modifier = Modifier.size(26.dp),
                                        color = accent,
                                        trackColor = Color(0x22FFFFFF),
                                        // Stroke.width 是 Float（像素）
                                        stroke = Stroke(
                                            width = with(LocalDensity.current) { 2.5.dp.toPx() },
                                            cap = StrokeCap.Round,
                                        ),
                                        // 官方默认振幅 ramp：65% 处于中段 → 满幅波浪
                                        amplitude = { p ->
                                            if (p <= 0.1f || p >= 0.95f) 0f else 1f
                                        },
                                    )
                                }
                            },
                            colors = accentRadioColors(accent),
                        )
                        Text(
                            "二选一切换，立即生效；波浪圆环为 M3E 官方 CircularWavyProgressIndicator 构件：波浪进度环围绕播放/暂停圆形按钮转动，轨道是平滑弧线，进度是沿弧流动的波浪，暂停时恢复平滑圆环；播放器按钮均为 M3E 官方构件，按压时带官方挤压动画",
                            fontSize = 8.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // 进度环半径（占屏幕短边比例，可缩至很小）
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("进度环半径 ${(ringRadiusFrac * 100).toInt()}%", fontSize = 11.sp, color = accent)
                        Slider(
                            value = ringRadiusFrac.coerceIn(0.05f, 0.50f),
                            onValueChange = { vm.setRingRadiusFrac(it) },
                            steps = 45,
                            valueRange = 0.05f..0.50f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = accentSliderColors(accent),
                        )
                        // 说明上下限：50% 时圆环直径 = 屏幕短边（顶到圆屏边缘，为物理上限）；
                        // 下限可缩到很小，满足「尽可能小」的需求
                        Text(
                            "50% 为物理上限（圆环顶到屏幕边缘），最小可缩至 5%",
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
                        Text("进度环粗细 ${ringStroke.toInt()}dp", fontSize = 11.sp, color = accent)
                        Slider(
                            value = ringStroke,
                            onValueChange = { vm.setRingStroke(it) },
                            steps = 10,
                            valueRange = 2f..12f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = accentSliderColors(accent),
                        )
                    }
                }
                // 播放器背景（专辑封面作背景）：模糊强度 + 明度（暗化）可调
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("背景模糊 ${blurRadius.toInt()}dp", fontSize = 11.sp, color = accent)
                        Slider(
                            value = blurRadius,
                            onValueChange = { vm.setBlurRadius(it) },
                            steps = 40,
                            valueRange = 0f..40f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = accentSliderColors(accent),
                        )
                    }
                }
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val brightness = (100f - dimAlpha / 0.95f * 100f).coerceIn(0f, 100f)
                        Text("背景明度 ${brightness.toInt()}", fontSize = 11.sp, color = accent)
                        Slider(
                            value = brightness,
                            onValueChange = { vm.setBackgroundBrightness(it) },
                            steps = 20,
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = accentSliderColors(accent),
                        )
                    }
                }
                }

                // ========== 歌词（字号 / 颜色，默认折叠）==========
                collapsibleSection("歌词", secLyric, { secLyric = !secLyric }, accent) {

                // 歌词字号（12sp ~ 22sp，实时预览生效）
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("歌词字号 ${lyricFontSize.toInt()}sp", fontSize = 11.sp, color = accent)
                        Slider(
                            value = lyricFontSize,
                            onValueChange = { vm.setLyricFontSize(it) },
                            steps = 10,
                            valueRange = 12f..22f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = accentSliderColors(accent),
                        )
                    }
                }

                // 歌词颜色：跟随专辑封面主色，或用户从 M3 官方 tonal 色阶 / 自定义色号选择
                item {
                    RadioButton(
                        selected = lyricColorMode == 0,
                        onSelect = { vm.setLyricColorMode(0) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("跟随专辑封面", fontSize = 11.sp) },
                        secondaryLabel = {
                            Text(if (lyricColorMode == 0) "当前使用" else "未使用", fontSize = 9.sp)
                        },
                        colors = accentRadioColors(accent),
                    )
                }
                item {
                    RadioButton(
                        selected = lyricColorMode == 1,
                        onSelect = { vm.setLyricColorMode(1) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("自定义颜色", fontSize = 11.sp) },
                        secondaryLabel = { Text("M3 官方色阶 / 填十六进制色号", fontSize = 9.sp) },
                        colors = accentRadioColors(accent),
                    )
                }

                // —— M3 官方 tonal 色阶（Primary tone 10→95）——
                item {
                    Text(
                        "Primary 色阶 · tone 10 → 95",
                        fontSize = 8.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    LyricPaletteRow(
                        colors = TONAL_PALETTE.subList(0, 11),
                        selected = lyricColorMode == 1 && TONAL_PALETTE.subList(0, 11).contains(lyricCustomColor),
                        selectedColor = lyricCustomColor,
                        onPick = { vm.setLyricCustomColor(it) },
                    )
                }
                // —— Secondary / Tertiary / Error / Neutral（tone 40 / 80）——
                item {
                    Text(
                        "Secondary / Tertiary / Error / Neutral · tone 40 / 80",
                        fontSize = 8.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    LyricPaletteRow(
                        colors = TONAL_PALETTE.subList(11, TONAL_PALETTE.size),
                        selected = lyricColorMode == 1 && TONAL_PALETTE.subList(11, TONAL_PALETTE.size).contains(lyricCustomColor),
                        selectedColor = lyricCustomColor,
                        onPick = { vm.setLyricCustomColor(it) },
                    )
                }

                // —— 自定义：直接填十六进制色号（#RRGGBB / #AARRGGBB / RRGGBB）——
                item {
                    HexColorInput(
                        accent = accent,
                        onApply = { vm.setLyricCustomColor(it) },
                    )
                }
                }

                // ========== 歌词切换动画（多动画可选 + 实时预览，默认折叠）==========
                collapsibleSection("歌词切换动画", secLyricAnim, { secLyricAnim = !secLyricAnim }, accent) {
                items(LyricTransition.entries.size) { i ->
                    val anim = LyricTransition.entries[i]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.setLyricTransition(anim) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RadioButton(
                            selected = lyricTransition == anim,
                            onSelect = { vm.setLyricTransition(anim) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(anim.label, fontSize = 11.sp) },
                            secondaryLabel = { Text(anim.desc, fontSize = 9.sp) },
                            colors = accentRadioColors(accent),
                        )
                        // 实时预览：迷你歌词行循环演示该动画的切换效果
                        LyricTransitionPreview(anim, accent)
                    }
                }
                }

                // ========== 6. 局域网（传歌 / 一起听，默认折叠）==========
                collapsibleSection("局域网", secLan, { secLan = !secLan }, accent) {
                item {
                    Card(
                        onClick = onOpenLanTransfer,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(percent = 50),
                        colors = accentCardColors(accent),
                    ) {
                        Text("传歌", fontSize = 11.sp)
                        Text("同一 Wi-Fi 下互传歌曲文件", fontSize = 9.sp)
                    }
                }
                item {
                    Card(
                        onClick = onOpenListenTogether,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(percent = 50),
                        colors = accentCardColors(accent),
                    ) {
                        Text("一起听", fontSize = 11.sp)
                        Text("把当前歌曲+歌词推给附近设备", fontSize = 9.sp)
                    }
                }
                // 压缩阈值：超过该体积的文件先压缩再传，对方收到后自动解压
                item {
                    val thresholdMb by vm.lanCompressThresholdMb.collectAsState()
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("超过 $thresholdMb MB 先压缩", fontSize = 11.sp, color = accent)
                        Slider(
                            value = thresholdMb.toFloat(),
                            onValueChange = { vm.setLanCompressThresholdMb(it.toInt()) },
                            steps = 32,
                            valueRange = 1f..100f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = accentSliderColors(accent),
                        )
                    }
                }
                }

                // ========== 7. 工具（默认展开）==========
                collapsibleSection("工具", secTool, { secTool = !secTool }, accent) {
                item {
                    Card(
                        onClick = onOpenCoverTool,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(percent = 50),
                        colors = accentCardColors(accent),
                    ) {
                        Text("封面工具", fontSize = 11.sp)
                        Text("内置文件管理器多选图片 · 匹配并写入专辑封面", fontSize = 9.sp)
                    }
                }
                }

                // ========== 8. 关于（默认折叠）==========
                collapsibleSection("关于", secAbout, { secAbout = !secAbout }, accent) {

                // 播放器主题：仅保留 M3E 一套 UI（经典主题已移除），无需再切换
                item {
                    Card(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(percent = 50),
                        colors = accentCardColors(accent),
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
                        shape = RoundedCornerShape(percent = 50),
                        colors = accentCardColors(accent),
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
                            shape = RoundedCornerShape(percent = 50),
                            colors = CardDefaults.cardColors(),
                        ) {
                            Text(p.name, fontSize = 9.sp, maxLines = 2)
                            Text(p.license, fontSize = 8.sp, color = accent)
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
            }

            // 微调模式覆盖提示条：选中频段后，表冠直接调增益
            if (active && selectedBand != null) {
                val bandName = eqFreqs.getOrNull(selectedBand!!)?.let { formatFreq(it) } ?: ""
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(Color(0xCC06070A), RoundedCornerShape(bottomStart = 17.dp, bottomEnd = 17.dp))
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
                        "🎚 $bandName",
                        color = accent,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

// ---------------- 主题色衍生的 M3E 组件颜色（#82：列表组件颜色跟随主题色） ----------------

/**
 * 设置页可折叠分组：标题卡点按展开/收起，展开后显示分组内的所有条目。
 * 标题卡用强调色文字 + 展开指示箭头（▲ 展开 / ▼ 折叠），整卡可点。
 * 作为 ScalingLazyListScope 的扩展使用，分组内容以 [content] 传入。
 */
private fun ScalingLazyListScope.collapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    accent: Color,
    content: ScalingLazyListScope.() -> Unit,
) {
    item {
        Card(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(percent = 50),
            colors = accentCardColors(accent),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    fontSize = 12.sp,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (expanded) "▲" else "▼",
                    fontSize = 11.sp,
                    color = accent,
                )
            }
        }
    }
    if (expanded) {
        content()
    }
}

/** Card 容器/内容/标题色由强调色衍生 */
@Composable
private fun accentCardColors(accent: Color) = CardDefaults.cardColors(
    containerColor = CARD_BG,
    contentColor = Color.White,
    appNameColor = accent,
    timeColor = Color.Gray,
    titleColor = accent,
    subtitleColor = Color.Gray,
)

/** RadioButton 选中态容器/指示器用强调色 */
@Composable
private fun accentRadioColors(accent: Color) = RadioButtonDefaults.radioButtonColors(
    selectedContainerColor = accent.copy(alpha = 0.22f),
    selectedContentColor = Color.White,
    selectedSecondaryContentColor = accent,
    selectedIconColor = accent,
    selectedControlColor = accent,
    unselectedContainerColor = CARD_BG,
    unselectedContentColor = Color.White,
    unselectedSecondaryContentColor = Color.Gray,
    unselectedIconColor = Color.Gray,
    unselectedControlColor = Color.Gray,
)

/** SwitchButton 选中态容器/轨道/滑块用强调色 */
@Composable
private fun accentSwitchColors(accent: Color) = SwitchButtonDefaults.switchButtonColors(
    checkedContainerColor = accent.copy(alpha = 0.22f),
    checkedContentColor = Color.White,
    checkedSecondaryContentColor = accent,
    checkedIconColor = accent,
    checkedThumbColor = accent,
    checkedThumbIconColor = Color.White,
    checkedTrackColor = accent.copy(alpha = 0.45f),
    checkedTrackBorderColor = accent,
    uncheckedContainerColor = CARD_BG,
    uncheckedContentColor = Color.White,
    uncheckedSecondaryContentColor = Color.Gray,
    uncheckedIconColor = Color.Gray,
    uncheckedThumbColor = Color.Gray,
    uncheckedTrackColor = Color(0x33FFFFFF),
    uncheckedTrackBorderColor = Color.Gray,
)

/** Slider 滑槽/已选条/按钮图标用强调色 */
@Composable
private fun accentSliderColors(accent: Color) = SliderDefaults.sliderColors(
    containerColor = CARD_BG,
    buttonIconColor = accent,
    selectedBarColor = accent,
    unselectedBarColor = Color(0x33FFFFFF),
    selectedBarSeparatorColor = Color.White,
    unselectedBarSeparatorColor = Color.Gray,
)

// ---------------- M3 tonal 色阶选择行 + 自定义十六进制输入（#81） ----------------

/** 一行圆形色板；命中当前自定义色时加白圈高亮 */
@Composable
private fun LyricPaletteRow(
    colors: List<Int>,
    selected: Boolean,
    selectedColor: Int,
    onPick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEach { c ->
            val isSel = selected && selectedColor == c
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(c), CircleShape)
                    .then(
                        if (isSel) Modifier.border(2.dp, Color.White, CircleShape) else Modifier
                    )
                    .clickable { onPick(c) }
            )
        }
    }
}

/**
 * 自定义歌词色：直接填十六进制色号（#RRGGBB / #AARRGGBB / RRGGBB）。
 * 输入合法（6 或 8 位十六进制）时点「应用」立即写入并切到自定义模式；
 * 非法输入时输入框描红提示，不写入。
 */
@Composable
private fun HexColorInput(accent: Color, onApply: (Int) -> Unit) {
    var hex by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    if (bad) Color(0x33FF5A7A) else Color(0x22FFFFFF),
                    RoundedCornerShape(percent = 50),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (hex.isEmpty()) {
                Text("#RRGGBB 或 #AARRGGBB", fontSize = 9.sp, color = Color.Gray)
            }
            BasicTextField(
                value = hex,
                onValueChange = {
                    hex = it
                    bad = false
                },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 10.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            "应用",
            fontSize = 10.sp,
            color = accent,
            modifier = Modifier
                .clickable {
                    val parsed = parseHexColor(hex)
                    if (parsed != null) {
                        onApply(parsed)
                        bad = false
                        hex = ""
                    } else {
                        bad = true
                    }
                }
                .padding(4.dp),
        )
    }
}

/** 解析十六进制色号：支持 #RRGGBB / #AARRGGBB / RRGGBB / AARRGGBB；不合法返回 null */
internal fun parseHexColor(raw: String): Int? {
    val s = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    if (s.length != 6 && s.length != 8) return null
    if (!s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return try {
        if (s.length == 6) {
            0xFF000000.toInt() or s.toInt(16)
        } else {
            s.toLong(16).toInt()
        }
    } catch (_: NumberFormatException) {
        null
    }
}

// ---------------- 竖条频段调节（形态移植自 AndroidEqualizer 的 EqualizerView） ----------------

/**
 * EQ 竖条组：每频段一根竖向滑条，顶部 dB 值、底部频率标签，0dB 处居中刻度线。
 * 支持点按定位 + 上下拖动调增益（拖动即选中该频段，随后可旋转表冠微调）。
 */
@Composable
private fun EqBandBars(
    freqs: List<Int>,
    gains: List<Int>,
    selected: Int?,
    accent: Color,
    onChange: (band: Int, db: Float) -> Unit,
) {
    val maxAbsDb = 15f
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        val n = freqs.size
        // 圆屏宽度有限：条宽随频段数自适应（5 段约 30dp，8+ 段收窄到 20dp 也放得下）
        val barW = ((maxWidth.value * 0.78f) / n).coerceIn(18f, 30f).dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            for (i in 0 until n) {
                val db = (gains.getOrElse(i) { 0 } / 100f).coerceIn(-maxAbsDb, maxAbsDb)
                val sel = selected == i
                Column(
                    modifier = Modifier.width(barW),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "%+.1f".format(db),
                        fontSize = 7.sp,
                        color = if (sel) accent else Color.Gray,
                        maxLines = 1,
                    )
                    EqBandBar(
                        db = db,
                        selected = sel,
                        accent = accent,
                        onChange = { onChange(i, it) },
                    )
                    Text(
                        text = formatFreq(freqs[i]),
                        fontSize = 8.sp,
                        color = if (sel) accent else Color.Gray,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 单根竖条：0dB 居中刻度，填充色强调，旋钮白点，拖动/点按调增益 */
@Composable
private fun EqBandBar(
    db: Float,
    selected: Boolean,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    val maxAbsDb = 15f
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 点按即定位：把手指位置映射为增益
                    val h = size.height.toFloat()
                    val v = (1f - down.position.y / h) * 2f - 1f
                    onChange((v * maxAbsDb).coerceIn(-maxAbsDb, maxAbsDb))
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp()) break
                        if (change.position != change.previousPosition) {
                            val hh = size.height.toFloat()
                            val vv = (1f - change.position.y / hh) * 2f - 1f
                            onChange((vv * maxAbsDb).coerceIn(-maxAbsDb, maxAbsDb))
                            change.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(66.dp)) {
            val w = size.width
            val h = size.height
            val trackW = with(density) { 9.dp.toPx() }
            val r = CornerRadius(trackW / 2f)
            val pad = with(density) { 5.dp.toPx() }
            val x = (w - trackW) / 2f
            // 底轨
            drawRoundRect(
                color = Color(0x33FFFFFF),
                topLeft = Offset(x, pad),
                size = Size(trackW, h - 2 * pad),
                cornerRadius = r,
            )
            // 0dB 居中刻度线
            drawLine(
                color = Color(0x66FFFFFF),
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = with(density) { 1.dp.toPx() },
            )
            // 填充（中线 → 当前值）
            val y = (h / 2f - (db / maxAbsDb) * (h / 2f - pad))
                .coerceIn(pad, h - pad)
            drawRoundRect(
                color = accent,
                topLeft = Offset(x, minOf(y, h / 2f)),
                size = Size(trackW, abs(h / 2f - y).coerceAtLeast(with(density) { 3.dp.toPx() })),
                cornerRadius = r,
            )
            // 旋钮
            drawCircle(
                color = Color.White,
                radius = with(density) { if (selected) 5.5f.dp.toPx() else 4f.dp.toPx() },
                center = Offset(w / 2f, y),
            )
        }
    }
}

// ---------------- 频率响应曲线 ----------------

/**
 * 频率响应曲线：横轴为频段中心频率（对数轴 20Hz–20kHz），纵轴为增益（dB，范围 ±15）。
 * 把每个频段的增益连成折线并在数据点画圆点，直观反映当前均衡器形状；增益变化时实时重绘。
 */
@Composable
private fun EqCurve(
    freqs: List<Int>,
    gains: List<Int>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
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
            color = accent,
            style = Stroke(width = 2.dp.toPx()),
        )
        // 数据点
        freqs.forEachIndexed { i, f ->
            drawCircle(
                color = accent,
                radius = 3.dp.toPx(),
                center = Offset(xOf(f), yOf(dbOf(gains.getOrElse(i) { 0 }))),
            )
        }
    }
}

private fun formatFreq(hz: Int): String =
    if (hz >= 1000) "${hz / 1000}k" else "$hz"
