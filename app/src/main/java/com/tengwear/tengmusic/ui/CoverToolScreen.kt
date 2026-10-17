package com.tengwear.tengmusic.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import coil.compose.AsyncImage
import com.tengwear.tengmusic.PlayerViewModel
import com.tengwear.tengmusic.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image

/**
 * 封面工具：给音频文件（MP3）写入专辑封面。
 *
 * 流程：点选一首歌 → 系统照片选择器选图 → 居中裁方 + 压缩为 512px JPEG →
 * 通过 SAF 覆盖写回文件（ID3v2.3 APIC 帧，见 Id3Tag）。
 *
 * 界面全部使用 M3E 官方组件（ScalingLazyColumn / Card / ListHeader / Icon）。
 */
@Composable
fun CoverToolScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val tracks by vm.tracks.collectAsState()
    val coverMessage by vm.coverMessage.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingTrack by remember { mutableStateOf<Track?>(null) }

    // 系统照片选择器（Photo Picker，无需存储权限）
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val t = pendingTrack
        pendingTrack = null
        if (uri != null && t != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    try {
                        val raw = context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        } ?: return@withContext null
                        // 居中裁方 → 缩到 512 → JPEG 压缩
                        val side = minOf(raw.width, raw.height)
                        val x = (raw.width - side) / 2
                        val y = (raw.height - side) / 2
                        val square = Bitmap.createBitmap(raw, x, y, side, side)
                        val scaled = Bitmap.createScaledBitmap(square, 512, 512, true)
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                        out.toByteArray()
                    } catch (_: Throwable) {
                        null
                    }
                }
                if (bytes != null) {
                    vm.writeTrackCoverArt(t, bytes, "image/jpeg")
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF06070A))) {
        Column(Modifier.fillMaxSize()) {
            // 标题栏：返回 + 标题
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回设置",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp).clip(CircleShape)
                        .clickable(onClick = onClose),
                )
                Text("封面工具", fontSize = 14.sp, color = Color.White)
                Spacer(Modifier.width(18.dp))
            }

            // 操作结果提示
            val msg = coverMessage
            if (msg != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable { vm.clearCoverMessage() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = msg,
                        fontSize = 10.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { ListHeader { Text("选择歌曲") } }
                items(tracks.size) { i ->
                    val t = tracks[i]
                    Card(
                        onClick = {
                            pendingTrack = t
                            pickLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(0.92f),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xF20B0B0E),
                            contentColor = Color.White,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = t.albumArtUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x22FFFFFF)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, fontSize = 11.sp, maxLines = 1)
                                Text(
                                    "${t.artist} · ${t.album}",
                                    fontSize = 8.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = "写入封面",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
