package com.tengwear.tengmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.tengwear.tengmusic.CustomAlbum
import com.tengwear.tengmusic.PlayerViewModel

/** 强调色：跟随全局主题色（专辑主色/自定义色/皮肤色），不再写死蓝色 */
private fun MG_ACCENT(): Color = Color(com.tengwear.tengmusic.AppTheme.accent.value)

/**
 * 自定义专辑管理页（v1.47）：
 *  - 新建 / 删除专辑，专辑排序（上移 / 下移，作用于专辑流顺序）
 *  - 进入专辑：增删歌曲（曲库点选）、歌曲内部排序、自定义封面（内置文件管理器）
 *
 * 编辑状态：editingId == null → 专辑列表；否则该专辑的编辑视图。
 */
@Composable
fun CustomAlbumManageScreen(
    vm: PlayerViewModel,
    onBack: () -> Unit,
    onPickCover: (albumId: Long) -> Unit,
    active: Boolean = true,
) {
    val customAlbums by vm.customAlbums.collectAsState()
    val tracks by vm.tracks.collectAsState()
    var editingId by remember { mutableStateOf<Long?>(null) }
    var newName by remember { mutableStateOf("") }
    var addingSongs by remember { mutableStateOf(false) }

    val listState = rememberScalingLazyListState()
    val focusRequester = rememberRotaryFocusRequester(active)

    val editing = customAlbums.firstOrNull { it.id == editingId }
    val tracksById = remember(tracks) { tracks.associateBy { it.id } }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .rotaryScroll(listState, focusRequester),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (editing == null) {
            // ================= 专辑列表 =================
            item {
                Text("自定义专辑", fontSize = 14.sp, color = MG_ACCENT())
                Text("出现在专辑流中，可增删歌 / 换封面 / 排序", fontSize = 9.sp, color = Color.Gray)
            }

            // 新建：输入名字 + 按钮
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .background(Color(0x1AFFFFFF), RoundedCornerShape(percent = 50))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (newName.isEmpty()) {
                            Text("新专辑名字…", fontSize = 11.sp, color = Color(0x66FFFFFF))
                        }
                        BasicTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 11.sp, color = Color.White),
                            cursorBrush = SolidColor(MG_ACCENT()),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    CompactButton(
                        onClick = {
                            vm.addCustomAlbum(newName)
                            newName = ""
                        },
                        enabled = newName.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "新建", modifier = Modifier.size(14.dp))
                    }
                }
            }

            items(customAlbums.size) { i ->
                val ca = customAlbums[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingId = ca.id }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 小封面
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Album, null, tint = Color.Gray, modifier = Modifier.size(15.dp))
                        AsyncImage(
                            model = ca.coverUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ca.name, fontSize = 12.sp, maxLines = 1)
                        Text("${ca.trackIds.size} 首", fontSize = 9.sp, color = Color.Gray)
                    }
                    // 排序按钮（作用于专辑流顺序）
                    CompactButton(
                        onClick = { vm.moveAlbumOrder("custom:${ca.id}", true) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Filled.ArrowUpward, "上移", modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    CompactButton(
                        onClick = { vm.moveAlbumOrder("custom:${ca.id}", false) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Filled.ArrowDownward, "下移", modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    CompactButton(
                        onClick = { vm.deleteCustomAlbum(ca.id) },
                        modifier = Modifier.size(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x55803B3B), contentColor = Color.White,
                        ),
                    ) {
                        Icon(Icons.Filled.Close, "删除", modifier = Modifier.size(12.dp))
                    }
                }
            }

            if (customAlbums.isEmpty()) {
                item {
                    Text(
                        "还没有自定义专辑\n在上方输入名字创建一个",
                        fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            item {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A2D36), contentColor = Color.White,
                    ),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Text("返回设置", fontSize = 11.sp)
                }
            }
        } else {
            // ================= 专辑编辑视图 =================
            item {
                Text("编辑 · ${editing!!.name}", fontSize = 13.sp, color = MG_ACCENT(), maxLines = 1)
            }

            // 封面行：点击打开内置文件管理器选图
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x22FFFFFF))
                            .clickable { onPickCover(editing!!.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Image, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        AsyncImage(
                            model = editing!!.coverUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("点封面换图（内置文件管理器）", fontSize = 10.sp, color = Color.White)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactButton(onClick = { addingSongs = !addingSongs }) {
                        Text(if (addingSongs) "收起曲库" else "添加歌曲", fontSize = 10.sp)
                    }
                    CompactButton(
                        onClick = { editingId = null; addingSongs = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2A2D36), contentColor = Color.White,
                        ),
                    ) {
                        Text("完成", fontSize = 10.sp)
                    }
                }
            }

            if (addingSongs) {
                // 曲库点选：点即加入（已在专辑里的显示为已加）
                item { Text("点歌曲加入专辑：", fontSize = 9.sp, color = Color.Gray) }
                items(tracks.size) { i ->
                    val t = tracks[i]
                    val inAlbum = t.id in editing!!.trackIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.addTrackToCustomAlbum(editing!!.id, t.id) }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (inAlbum) Icons.Filled.Album else Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = if (inAlbum) MG_ACCENT() else Color.Gray,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            t.title, fontSize = 11.sp, maxLines = 1,
                            color = if (inAlbum) MG_ACCENT() else Color.White,
                        )
                    }
                }
            } else {
                // 专辑内歌曲：每行 上移 / 下移 / 移除
                item { Text("专辑内歌曲（↑↓ 调整顺序）：", fontSize = 9.sp, color = Color.Gray) }
                items(editing!!.trackIds.size) { i ->
                    val tid = editing.trackIds[i]
                    val t = tracksById[tid]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t?.title ?: "未知曲目", fontSize = 11.sp, maxLines = 1)
                        }
                        CompactButton(
                            onClick = { vm.moveTrackInCustomAlbum(editing.id, i, true) },
                            modifier = Modifier.size(26.dp),
                        ) { Icon(Icons.Filled.ArrowUpward, "上移", modifier = Modifier.size(11.dp)) }
                        Spacer(Modifier.width(3.dp))
                        CompactButton(
                            onClick = { vm.moveTrackInCustomAlbum(editing.id, i, false) },
                            modifier = Modifier.size(26.dp),
                        ) { Icon(Icons.Filled.ArrowDownward, "下移", modifier = Modifier.size(11.dp)) }
                        Spacer(Modifier.width(3.dp))
                        CompactButton(
                            onClick = { vm.removeTrackFromCustomAlbum(editing.id, tid) },
                            modifier = Modifier.size(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x55803B3B), contentColor = Color.White,
                            ),
                        ) { Icon(Icons.Filled.Close, "移除", modifier = Modifier.size(11.dp)) }
                    }
                }
                if (editing.trackIds.isEmpty()) {
                    item {
                        Text("专辑还是空的，点「添加歌曲」从曲库里挑", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
