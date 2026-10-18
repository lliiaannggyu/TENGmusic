package com.tengwear.tengmusic.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.documentfile.provider.DocumentFile
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 内置文件管理器（图片多选）：用 SAF（Storage Access Framework）让用户授权一个目录，
 * 之后在应用内列出该目录下的「文件夹（可进入）/ 图片文件（可多选）」，完全替代系统照片选择器。
 *
 * 为什么不用系统照片选择器：系统 Photo Picker 在手表上交互笨重、多选不便，且无法按文件夹浏览。
 * 本组件是 App 自己的文件管理器 UI —— 目录由用户用系统目录选择器一次性授权（SAF tree），
 * 之后所有列出/导航/勾选都是我们自己的界面，多选图片后点「完成」把选中的图片 Uri 回传给调用方。
 *
 * 选中图片以 document Uri 形式返回，调用方（封面工具）可直接用 contentResolver 读取字节，
 * 无需任何存储权限（SAF 已授权）。
 *
 * @param onResult 用户点「完成」后回传被选中的图片 Uri 列表（已去重）
 * @param onClose  关闭文件管理器（取消选择）
 */
@Composable
fun FilePickerScreen(
    onResult: (List<Uri>) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    // 已授权的目录树根（SAF tree）
    var tree by remember { mutableStateOf<DocumentFile?>(null) }
    // 当前所在的文件夹
    var currentFolder by remember { mutableStateOf<DocumentFile?>(null) }
    // 进入子文件夹时的父目录栈（支持「返回上级」）
    val folderStack = remember { mutableStateListOf<DocumentFile>() }
    // 已勾选的图片 Uri
    val selected = remember { mutableStateListOf<Uri>() }

    // 系统目录选择器：用户选一个目录，App 取得该目录子树的长期读权限
    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // 持久化读权限，方便后续访问该目录子树（失败不影响本次读取，可忽略）
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Throwable) {
            }
            val root = DocumentFile.fromTreeUri(context, uri)
            tree = root
            currentFolder = root
            folderStack.clear()
            selected.clear()
        }
    }

    // 当前文件夹下的内容（子文件夹 + 文件），异步加载（DocumentFile.listFiles 走 ContentProvider，放 IO 线程）
    var children by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(currentFolder) {
        val f = currentFolder ?: return@LaunchedEffect
        loading = true
        children = withContext(Dispatchers.IO) { f.listFiles().orEmpty().toList() }
        loading = false
    }

    fun openFolder(folder: DocumentFile) {
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
            // 顶部栏：关闭（×）/ 返回上级 / 当前目录名 / 完成（N）
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
                val label = currentFolder?.name
                    ?: if (tree == null) "选择文件夹" else "文件管理器"
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
                // 尚未授权目录：提示并引导用户选择图片所在文件夹
                if (tree == null) {
                    item {
                        Text(
                            "选择一个文件夹以浏览其中的图片\n（用于批量写入专辑封面）",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    item {
                        Card(
                            onClick = { treeLauncher.launch(null) },
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
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = Color(0xFF98EAF9),
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("选择图片所在文件夹", fontSize = 11.sp)
                            }
                        }
                    }
                    return@ScalingLazyColumn
                }

                val folder = currentFolder
                if (folder == null) {
                    item {
                        Text("目录为空", fontSize = 10.sp, color = Color.Gray)
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

                    // 图片文件：点按勾选/取消（多选）
                    items(images) { img ->
                        val uri = img.uri
                        val isSel = selected.contains(uri)
                        Card(
                            onClick = {
                                if (isSel) selected.remove(uri) else selected.add(uri)
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
                                    model = uri,
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

/** 判断一个 DocumentFile 是否为图片（按 MIME 类型或扩展名兜底） */
private fun isImageFile(doc: DocumentFile): Boolean {
    if (!doc.isFile) return false
    val type = doc.type
    if (type != null) return type.startsWith("image/")
    val name = doc.name?.lowercase() ?: return false
    return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
        name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".bmp")
}
