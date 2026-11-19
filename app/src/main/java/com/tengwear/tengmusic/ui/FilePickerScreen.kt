package com.tengwear.tengmusic.ui

import android.content.Context
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 强调色：跟随全局主题色；带透明版本用于选中态底色 */
private fun themeAccent(): Color = Color(com.tengwear.tengmusic.AppTheme.accent.value)
private fun themeAccentDim(): Color = com.tengwear.tengmusic.AppTheme.withAlpha(com.tengwear.tengmusic.AppTheme.accent.value, 0x22)


/** 图片扩展名集合（封面工具用） */
val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
/** 主题文件扩展名（主题工坊用） */
val THEME_EXTENSIONS = setOf("json")

/**
 * 内置文件管理器（通用多选）：从内部存储根目录自由浏览任意目录、勾选指定类型文件。
 *
 * 关键修复：不再锁死上次的目录（那会把人困在空文件夹里）。每次进入都从内部存储
 * 根目录 [root] 出发；返回上级用 parentFile 判断，永远可用，一路可退回根目录。
 * 零权限、不调系统选择器，用 java.io.File 直接列目录。
 *
 * @param allowedExtensions 允许勾选/显示的文件扩展名（小写，不含点）；空集合表示显示全部文件
 * @param startDir          可选起始目录；仅当它位于内部存储根之内才生效，否则忽略（防止被困在私有空目录）
 * @param title             根目录栏显示名
 * @param onResult          点「完成」后回传选中的文件列表
 * @param onClose           关闭（取消）
 */
@Composable
fun FilePickerScreen(
    onResult: (List<File>) -> Unit,
    onClose: () -> Unit,
    allowedExtensions: Set<String> = IMAGE_EXTENSIONS,
    startDir: File? = null,
    title: String = "文件管理器",
) {
    // 自由浏览的根：内部存储根目录。传入的 startDir 若在根之内则用它，否则忽略。
    val root = remember {
        val r = android.os.Environment.getExternalStorageDirectory()
        val s = startDir
        if (s != null && s.isDirectory &&
            (s.absolutePath == r.absolutePath || s.absolutePath.startsWith(r.absolutePath + File.separator))
        ) s else r
    }
    // 每次进入都从根目录开始，绝不恢复上次目录（那正是「固定文件夹」的元凶）。
    var currentFolder by remember { mutableStateOf<File>(root) }
    val selected = remember { mutableStateListOf<File>() }
    var reloadKey by remember { mutableStateOf(0) }

    var children by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(currentFolder, reloadKey) {
        val f = currentFolder
        loading = true
        children = withContext(Dispatchers.IO) {
            f.listFiles()?.toList()?.filter { it.exists() } ?: emptyList()
        }
        loading = false
    }

    // 能否返回上级：只要还没到根，且父目录仍在内部存储根之内，就允许返回。
    fun canGoUp(): Boolean {
        val p = currentFolder.parentFile ?: return false
        if (currentFolder.absolutePath == root.absolutePath) return false
        return p.absolutePath == root.absolutePath ||
            p.absolutePath.startsWith(root.absolutePath + File.separator)
    }
    fun goUp() {
        val p = currentFolder.parentFile ?: return
        if (p.absolutePath == root.absolutePath ||
            p.absolutePath.startsWith(root.absolutePath + File.separator)
        ) {
            currentFolder = p
        }
    }
    fun openFolder(folder: File) {
        if (folder.isDirectory) currentFolder = folder
    }
    fun matches(f: File): Boolean {
        if (!f.isFile) return false
        if (allowedExtensions.isEmpty()) return true
        val ext = f.name.substringAfterLast('.', "").lowercase()
        return ext in allowedExtensions
    }

    val currentPath = currentFolder.absolutePath
    val displayName = if (currentFolder.absolutePath == root.absolutePath) {
        "内部存储"
    } else {
        currentFolder.name ?: currentPath
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF06070A))) {
        Column(Modifier.fillMaxSize()) {
            // 顶部栏：关闭 / [返回上级] / 当前目录名 / 完成(N)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp).clip(RoundedCornerShape(percent = 50))
                            .clickable(onClick = onClose),
                    )
                    if (canGoUp()) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回上级",
                            tint = themeAccent(),
                            modifier = Modifier.size(16.dp).clip(RoundedCornerShape(percent = 50))
                                .clickable(onClick = { goUp() }),
                        )
                    }
                }
                Text(
                    text = displayName,
                    fontSize = 11.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                Text(
                    text = "完成(${selected.size})",
                    fontSize = 10.sp,
                    color = if (selected.isNotEmpty()) themeAccent() else Color.Gray,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (selected.isNotEmpty()) themeAccentDim() else Color(0x33FFFFFF))
                        .clickable(enabled = selected.isNotEmpty()) {
                            if (selected.isNotEmpty()) onResult(selected.toList())
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            val listState = rememberScalingLazyListState()
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 当前完整路径（一眼看清在哪）
                item {
                    Text(
                        currentPath,
                        fontSize = 8.sp,
                        color = Color(0x88FFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.94f).padding(horizontal = 6.dp),
                    )
                }

                // 返回上级（列表中也放一个，明显且好点）
                if (canGoUp()) {
                    item {
                        Card(
                            onClick = { goUp() },
                            modifier = Modifier.fillMaxWidth(0.92f),
                            shape = RoundedCornerShape(percent = 50),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xF20B0B0E), contentColor = Color.White,
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = themeAccent(),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("返回上级目录", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // 刷新
                item {
                    Card(
                        onClick = { reloadKey++ },
                        modifier = Modifier.fillMaxWidth(0.92f),
                        shape = RoundedCornerShape(percent = 50),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xF20B0B0E), contentColor = Color.White,
                        ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = themeAccent(),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("刷新目录", fontSize = 11.sp)
                        }
                    }
                }

                val folders = children.filter { it.isDirectory }
                    .sortedBy { it.name?.lowercase() ?: "" }
                val files = children.filter { matches(it) }
                    .sortedBy { it.name?.lowercase() ?: "" }

                if (loading) {
                    item { Text("正在读取…", fontSize = 10.sp, color = Color.Gray) }
                }

                // 子文件夹
                items(folders) { f ->
                    Card(
                        onClick = { openFolder(f) },
                        modifier = Modifier.fillMaxWidth(0.94f),
                        shape = RoundedCornerShape(percent = 50),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xF20B0B0E), contentColor = Color.White,
                        ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = themeAccent(),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(f.name ?: "文件夹", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }

                if (!loading && files.isEmpty() && folders.isEmpty()) {
                    item {
                        Text(
                            "该文件夹没有可显示的文件或子目录\n可点顶部返回上级换一个目录",
                            fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }

                // 文件：点按勾选/取消
                items(files) { file ->
                    val isSel = selected.contains(file)
                    val isImage = IMAGE_EXTENSIONS.any { file.name.endsWith(".$it", true) }
                    Card(
                        onClick = {
                            if (isSel) selected.remove(file) else selected.add(file)
                        },
                        modifier = Modifier.fillMaxWidth(0.94f),
                        shape = RoundedCornerShape(percent = 50),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSel) themeAccentDim() else Color(0xF20B0B0E),
                            contentColor = Color.White,
                        ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                        ) {
                            if (isImage) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x22FFFFFF)),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = themeAccent(),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name, fontSize = 11.sp, maxLines = 1)
                                val sizeKb = (file.length() / 1024).toInt()
                                Text(
                                    if (sizeKb > 0) "$sizeKb KB" else "",
                                    fontSize = 8.sp, color = Color.Gray, maxLines = 1,
                                )
                            }
                            if (isSel) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "已选中",
                                    tint = themeAccent(),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
