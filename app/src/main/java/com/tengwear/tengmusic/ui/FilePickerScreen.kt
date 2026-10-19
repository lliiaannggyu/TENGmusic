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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
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

/**
 * 内置文件管理器（图片多选）：直接在 App 自己的文件目录里浏览图片，
 * **不调用任何系统文件夹选择器、不申请任何存储权限**。
 *
 * 图片来源 = App 专属目录下的 covers 文件夹：
 *   context.getExternalFilesDir("covers")
 *   即 /.../Android/data/<包名>/files/covers
 * 用户用电脑 ADB 或手表文件传输把封面图放进去即可，App 用 java.io.File 直接列目录，
 * 支持进入子文件夹、返回上级、多选图片。
 *
 * 选中图片以 [File] 形式回传，调用方（封面工具）直接读字节，无需内容提供器 / 权限。
 *
 * @param onResult 用户点「完成」后回传被选中的图片 File 列表（已去重）
 * @param onClose  关闭文件管理器（取消选择）
 */
@Composable
fun FilePickerScreen(
    onResult: (List<File>) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    // 内置根目录：App 专属 covers 文件夹（零权限、可直接读）
    val root = remember { getCoversDir(context) }
    // 当前所在文件夹（默认根）
    var currentFolder by remember { mutableStateOf<File?>(null) }
    // 父目录栈（支持「返回上级」）
    val folderStack = remember { mutableStateListOf<File>() }
    // 已勾选的图片
    val selected = remember { mutableStateListOf<File>() }
    // 刷新计数：递增可强制重新扫描目录（放入新图后点刷新）
    var reloadKey by remember { mutableStateOf(0) }

    // 进入即定位到根目录
    LaunchedEffect(Unit) { currentFolder = root }

    // 当前文件夹下的内容（子文件夹 + 图片文件），IO 线程读取
    var children by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(currentFolder, reloadKey) {
        val f = currentFolder ?: return@LaunchedEffect
        loading = true
        children = withContext(Dispatchers.IO) {
            f.listFiles()?.toList()?.filter { it.exists() } ?: emptyList()
        }
        loading = false
    }

    fun openFolder(folder: File) {
        currentFolder?.let { folderStack.add(it) }
        currentFolder = folder
    }

    fun goUp() {
        if (folderStack.isNotEmpty()) {
            currentFolder = folderStack.removeAt(folderStack.lastIndex)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF06070A))) {
        Column(Modifier.fillMaxSize()) {
            // 顶部栏：关闭（×）/ 当前目录名 / 完成（N）
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp).clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = onClose),
                )
                val folder = currentFolder
                val label = if (folder == null) "文件管理器" else relativePath(root, folder)
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "完成(${selected.size})",
                    fontSize = 11.sp,
                    color = if (selected.isNotEmpty()) Color(0xFF98EAF9) else Color.Gray,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (selected.isNotEmpty()) Color(0x2298EAF9) else Color(0x33FFFFFF))
                        .clickable(enabled = selected.isNotEmpty()) {
                            if (selected.isNotEmpty()) onResult(selected.toList())
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            val listState = rememberScalingLazyListState()
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val folder = currentFolder
                if (folder == null) {
                    item {
                        Text("正在打开…", fontSize = 10.sp, color = Color.Gray)
                    }
                } else {
                    // 返回上级目录（仅在已进入子目录时显示）
                    if (folderStack.isNotEmpty()) {
                        item {
                            Card(
                                onClick = { goUp() },
                                modifier = Modifier.fillMaxWidth(0.92f),
                                shape = RoundedCornerShape(percent = 50),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xF20B0B0E),
                                    contentColor = Color.White,
                                ),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("返回上级目录", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // 刷新：重新扫描当前目录（把刚放入的图片显出来）
                    item {
                        Card(
                            onClick = { reloadKey++ },
                            modifier = Modifier.fillMaxWidth(0.92f),
                            shape = RoundedCornerShape(percent = 50),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xF20B0B0E),
                                contentColor = Color.White,
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFF98EAF9),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("刷新目录", fontSize = 11.sp)
                            }
                        }
                    }

                    // 根目录下的引导提示：告诉用户把图放到哪个文件夹
                    if (folder == root) {
                        item {
                            Text(
                                "把封面图放进本 App 的 covers 文件夹后点「刷新目录」\n" +
                                    "(可用电脑 ADB 传输：${root.absolutePath})",
                                fontSize = 8.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            )
                        }
                    }

                    val folders = children.filter { it.isDirectory }
                        .sortedBy { it.name?.lowercase() ?: "" }
                    val images = children.filter { isImageFile(it) }
                        .sortedBy { it.name?.lowercase() ?: "" }

                    if (loading) {
                        item {
                            Text("正在读取…", fontSize = 10.sp, color = Color.Gray)
                        }
                    }

                    // 子文件夹：点按进入
                    items(folders) { f ->
                        Card(
                            onClick = { openFolder(f) },
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
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = Color(0xFF98EAF9),
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.name ?: "文件夹", fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }

                    if (!loading && images.isEmpty() && folders.isEmpty()) {
                        item {
                            Text(
                                "该文件夹没有图片或子文件夹",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }

                    // 图片文件：点按勾选/取消（多选）+ 缩略图 + 文件大小
                    items(images) { img ->
                        val isSel = selected.contains(img)
                        Card(
                            onClick = {
                                if (isSel) selected.remove(img) else selected.add(img)
                            },
                            modifier = Modifier.fillMaxWidth(0.94f),
                            shape = RoundedCornerShape(percent = 50),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSel) Color(0x2298EAF9) else Color(0xF20B0B0E),
                                contentColor = Color.White,
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                            ) {
                                AsyncImage(
                                    model = img,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x22FFFFFF)),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(img.name ?: "图片", fontSize = 11.sp, maxLines = 1)
                                    val sizeKb = (img.length() / 1024).toInt()
                                    Text(
                                        if (sizeKb > 0) "$sizeKb KB" else "",
                                        fontSize = 8.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                    )
                                }
                                if (isSel) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "已选中",
                                        tint = Color(0xFF98EAF9),
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
}

/** App 专属 covers 文件夹（零权限、可直接读写） */
private fun getCoversDir(context: Context): File {
    val base = context.getExternalFilesDir(null) ?: context.filesDir
    return File(base, "covers").also { if (!it.exists()) it.mkdirs() }
}

/** 相对根目录的路径，用于顶部标题：根为 "covers"，子目录为 "covers/xxx" */
private fun relativePath(root: File, folder: File): String {
    val rootPath = root.absolutePath.trimEnd('/')
    val path = folder.absolutePath.trimEnd('/')
    if (path == rootPath) return "covers"
    val suffix = path.removePrefix(rootPath).trimStart('/')
    return if (suffix.isEmpty()) "covers" else "covers/$suffix"
}

/** 判断一个 File 是否为图片（按扩展名） */
private fun isImageFile(f: File): Boolean {
    if (!f.isFile) return false
    val name = f.name.lowercase()
    return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
        name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".bmp")
}
