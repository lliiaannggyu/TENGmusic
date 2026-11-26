package com.tengwear.tengmusic.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.tengwear.tengmusic.CustomThemeRepository
import com.tengwear.tengmusic.PlayerViewModel
import com.tengwear.tengmusic.ThemeFile

/**
 * 主题工坊（v1.48，v1.68 增强）：播放器 UI / 歌词 主题的导入、预览、切换、删除。
 *
 * - 主题是用户自己编写的 .json 文件，可通过内置文件管理器自由浏览目录导入；
 *   也可在「设置 → 主题文本导入」里粘贴 JSON 文本导入（v1.68）。
 * - 内置制作手册，可直接在手表查看、复制全文或导出到 Download 目录（v1.68）。
 * - 每个主题卡片带一块「缩略预览」，点按即应用；右侧 × 删除。
 * - 「恢复默认」清除当前分类的自定义主题。
 */
@Composable
fun ThemeManagerScreen(
    vm: PlayerViewModel,
    onClose: () -> Unit,
    active: Boolean = true,
) {
    val ctx = LocalContext.current
    val themeVersion by vm.themeVersion.collectAsState()
    val accentInt by com.tengwear.tengmusic.AppTheme.accent.collectAsState()
    val accent = Color(accentInt)

    var tab by remember { mutableStateOf(0) } // 0 播放器 1 歌词
    var mode by remember { mutableStateOf(0) } // 0 列表 1 手册
    var showPicker by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester(active && mode == 0)

    // themeVersion 参与读取，导入/删除后自动刷新
    val kind = if (tab == 0) "player" else "lyrics"
    val allThemes = remember(themeVersion) { CustomThemeRepository.list(vm.getApplication()) }
    val themes = allThemes.filter { it.kind == kind }
    val activeName = remember(themeVersion) {
        CustomThemeRepository.getActiveFileName(vm.getApplication(), kind)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF06070A))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    Icons.Filled.Close, contentDescription = "关闭",
                    tint = Color.White, modifier = Modifier.size(18.dp).clip(CircleShape)
                        .clickable(onClick = { if (mode != 0) mode = 0 else onClose() }),
                )
                Text(
                    when (mode) { 1 -> "制作手册"; else -> "主题工坊" },
                    fontSize = 13.sp, color = accent,
                )
                Spacer(Modifier.size(18.dp))
            }

            when (mode) {
                1 -> ManualView(accent = accent, onToast = { toast = it })
                else -> {
                    // 分类切换
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CompactButton(
                            onClick = { tab = 0 },
                            colors = androidx.wear.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (tab == 0) accent else Color(0xFF2A2D36),
                                contentColor = if (tab == 0) Color(0xFF06343B) else Color.White,
                            ),
                        ) { Text("播放器UI", fontSize = 10.sp) }
                        CompactButton(
                            onClick = { tab = 1 },
                            colors = androidx.wear.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (tab == 1) accent else Color(0xFF2A2D36),
                                contentColor = if (tab == 1) Color(0xFF06343B) else Color.White,
                            ),
                        ) { Text("歌词", fontSize = 10.sp) }
                    }

                    if (toast != null) {
                        Text(
                            toast!!, fontSize = 9.sp, color = accent, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }

                    ScalingLazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().weight(1f)
                            .rotaryScroll(listState, focusRequester),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item { Text("${if (tab == 0) "播放器" else "歌词"}主题", fontSize = 10.sp, color = Color.Gray) }

                        // 导入：文件管理器
                        item {
                            Card(
                                onClick = { showPicker = true },
                                modifier = Modifier.fillMaxWidth(0.94f),
                                shape = RoundedCornerShape(percent = 50),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xF20B0B0E), contentColor = accent,
                                ),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("导入主题文件", fontSize = 11.sp)
                                        Text("从文件管理器选 .json（可多选）", fontSize = 8.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }

                        // 制作手册（v1.68）
                        item {
                            Card(
                                onClick = { mode = 1 },
                                modifier = Modifier.fillMaxWidth(0.94f),
                                shape = RoundedCornerShape(percent = 50),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0x22FFFFFF), contentColor = Color.White,
                                ),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text("制作手册", fontSize = 11.sp)
                                    Text("查看 / 复制 / 导出 .json 编写教程", fontSize = 8.sp, color = Color.Gray)
                                }
                            }
                        }

                        if (themes.isEmpty()) {
                            item {
                                Text(
                                    "还没有主题\n点上方导入，或按手册制作 .json 文件",
                                    fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                )
                            }
                        }

                        items(themes) { theme ->
                            ThemeCard(
                                theme = theme,
                                isActive = theme.fileName == activeName,
                                accent = accent,
                                onApply = {
                                    vm.applyTheme(theme)
                                    toast = "已应用：${theme.name}"
                                    vm.notifyThemesChanged()
                                },
                                onDelete = {
                                    CustomThemeRepository.delete(vm.getApplication(), theme.fileName)
                                    vm.notifyThemesChanged()
                                    toast = "已删除：${theme.name}"
                                },
                            )
                        }

                        item {
                            Card(
                                onClick = {
                                    vm.clearTheme(kind)
                                    toast = "已恢复默认"
                                },
                                modifier = Modifier.fillMaxWidth(0.94f),
                                shape = RoundedCornerShape(percent = 50),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0x22FFFFFF), contentColor = Color.White,
                                ),
                            ) {
                                Text(
                                    "恢复默认主题", fontSize = 11.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                )
                            }
                        }
                        item { Spacer(Modifier.height(10.dp)) }
                    }
                }
            }
        }

        // toast 显示在最上层（粘贴/手册子页也需要）
        if (mode != 0 && toast != null) {
            Text(
                toast!!, fontSize = 9.sp, color = accent, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                    .background(Color(0xCC06070A), RoundedCornerShape(8.dp)).padding(8.dp),
            )
        }

        // 文件管理器：选 .json 主题文件，自由浏览目录
        if (showPicker) {
            FilePickerScreen(
                allowedExtensions = THEME_EXTENSIONS,
                startDir = null,
                title = "选择主题文件",
                onClose = { showPicker = false },
                onResult = { files ->
                    showPicker = false
                    var ok = 0
                    files.forEach { f ->
                        if (CustomThemeRepository.import(vm.getApplication(), f) != null) ok++
                    }
                    vm.notifyThemesChanged()
                    toast = if (ok > 0) "已导入 $ok 个主题" else "未识别到有效主题文件"
                },
            )
        }
    }
}

/** 内置制作手册子页：滚动查看 + 复制全文 + 导出到 Download */
@Composable
private fun ManualView(accent: Color, onToast: (String) -> Unit) {
    val ctx = LocalContext.current
    val manual = remember { CustomThemeRepository.readManual(ctx) }
    val listState = rememberScalingLazyListState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            androidx.wear.compose.material3.CompactButton(onClick = {
                runCatching {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("TENGMUSIC 主题手册", manual))
                    onToast("手册已复制到剪贴板")
                }.onFailure { onToast("复制失败") }
            }) { Text("复制全文", fontSize = 9.sp) }
            androidx.wear.compose.material3.CompactButton(onClick = {
                val ok = runCatching {
                    val dir = java.io.File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "TENGMUSIC-主题制作手册.md",
                    )
                    dir.writeText(manual)
                    true
                }.getOrDefault(false)
                onToast(if (ok) "已导出到 Download" else "导出失败")
            }) { Text("导出文件", fontSize = 9.sp) }
        }
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Text("声明式皮肤制作手册 v2", fontSize = 11.sp, color = accent, textAlign = TextAlign.Center) }
            // 按行显示手册（每段一行，保持可读）
            manual.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                item {
                    Text(
                        line, fontSize = 8.sp, color = Color(0xCCFFFFFF),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** 单个主题卡片：色块缩略预览 + 名称 + 应用状态 + 删除 */
@Composable
private fun ThemeCard(
    theme: ThemeFile,
    isActive: Boolean,
    accent: Color,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    // 从主题里取一个代表色做预览（兼容 v1 / v2）
    val swatch: Color = theme.player?.accent?.let { Color(it) }
        ?: theme.lyrics?.activeColor?.let { Color(it) }
        ?: theme.lyrics?.bgGlow?.let { Color(it) }
        ?: theme.playerSkin?.widgets?.firstOrNull { it.id == "progress" }?.color?.let { Color(it) }
        ?: theme.playerSkin?.widgets?.firstOrNull { it.id == "play" }?.bg?.let { Color(it) }
        ?: theme.lyricsSkin?.active?.color?.let { Color(it) }
        ?: theme.lyricsSkin?.bgGlow?.let { Color(it) }
        ?: accent
    Card(
        onClick = onApply,
        modifier = Modifier.fillMaxWidth(0.96f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0x222A2D36) else Color(0xF20B0B0E),
            contentColor = Color.White,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                    .background(swatch.copy(alpha = 0.25f))
                    .border(2.dp, swatch, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(swatch))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(theme.name, fontSize = 11.sp, maxLines = 1)
                Text(
                    if (isActive) "使用中" else theme.fileName,
                    fontSize = 8.sp, color = if (isActive) accent else Color.Gray, maxLines = 1,
                )
            }
            Icon(
                Icons.Filled.Close, contentDescription = "删除",
                tint = Color(0xFFFF8A9A),
                modifier = Modifier.size(20.dp).clip(CircleShape)
                    .background(Color(0x22FF8A9A))
                    .clickable(onClick = onDelete).padding(3.dp),
            )
        }
    }
}
