package com.tengwear.tengmusic.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
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
import androidx.compose.material.icons.filled.Close

/** 用户选中的一张待匹配图片（文件名 + 压缩后的 JPEG 字节） */
private class PickedImage(val name: String, val bytes: ByteArray)

/** 一条自动匹配结果：图片 ↔ 歌曲，score 为文件名重合字符数 */
private class CoverPair(val track: Track, val image: PickedImage, val score: Int)

/**
 * 封面工具：给音频文件（MP3）写入专辑封面。
 *
 * 两种模式（界面全部使用 M3E 官方组件）：
 * 1. **单曲写入**：点选一首歌 → 内置文件管理器（SAF 目录授权 + 应用内浏览）勾选一张图 →
 *    居中裁方 + 压缩为 512px JPEG → 通过 SAF 覆盖写回文件（ID3v2.3 APIC 帧，见 Id3Tag）。
 *    **已带封面的歌曲点击即为替换封面**，卡片右侧徽标显示「替换 / 添加」，写入成功后即时翻转。
 * 2. **批量匹配**（参考 GitHub 高星音乐标签编辑器 Lyrico 1259★ 的批量标签处理形态）：
 *    在文件管理器勾选多张图片 → 按「歌名/文件名 与 图片文件名 最长公共子串 ≥ 3 字符」自动判定
 *    对应封面 → **先把匹配结果列出来给用户看**（可逐条移除）→ 用户确认后批量写入。
 *
 * 未匹配上的图片不会写入任何歌曲；一首歌只分配一张图（重合字符最多者）。
 */
@Composable
fun CoverToolScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val tracks by vm.tracks.collectAsState()
    val coverMessage by vm.coverMessage.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingTrack by remember { mutableStateOf<Track?>(null) }
    // 已检测到 / 已写入封面的曲目 id 集合；用于在单曲列表显示「替换」徽标
    var coveredIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // 批量匹配状态
    var matching by remember { mutableStateOf(false) }
    var pairs by remember { mutableStateOf<List<CoverPair>>(emptyList()) }
    var unmatchedImages by remember { mutableStateOf<List<String>>(emptyList()) }

    // 内置文件管理器（图片多选）浮层开关
    var showFilePicker by remember { mutableStateOf(false) }

    // 文件管理器返回：单曲模式写第一张图；批量模式读图后自动匹配列给用户确认
    fun handlePicked(uris: List<android.net.Uri>) {
        showFilePicker = false
        if (uris.isEmpty()) return
        val t = pendingTrack
        pendingTrack = null
        scope.launch {
            if (t != null) {
                // 单曲写入：取第一张选中的图覆盖写回该曲目
                val bytes = withContext(Dispatchers.IO) { loadCoverBytes(context, uris.first()) }
                if (bytes != null) {
                    vm.writeTrackCoverArt(t, bytes, "image/jpeg")
                    coveredIds = coveredIds + t.id
                }
            } else {
                // 批量：读图 + 自动匹配
                matching = true
                val images = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        val name = queryDisplayName(context, uri) ?: "未命名图片"
                        val bytes = loadCoverBytes(context, uri) ?: return@mapNotNull null
                        PickedImage(name, bytes)
                    }
                }
                val (matched, unmatched) = withContext(Dispatchers.IO) {
                    matchCovers(tracks, images)
                }
                pairs = matched
                unmatchedImages = unmatched
                matching = false
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
                // ========== 批量匹配入口 ==========
                item { ListHeader { Text("批量封面") } }
                item {
                    Card(
                        onClick = {
                            pendingTrack = null
                            showFilePicker = true
                        },
                        modifier = Modifier.fillMaxWidth(0.92f),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xF20B0B0E),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("多选图片 · 自动匹配", fontSize = 11.sp)
                        Text(
                            "在文件管理器勾选图片 → 歌名/文件名与图片名重合 ≥3 字符即判定为对应封面",
                            fontSize = 8.sp,
                            color = Color.Gray,
                        )
                    }
                }

                if (matching) {
                    item {
                        Text(
                            "正在读取图片并匹配…",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // ========== 匹配结果（先列出来给用户看，可逐条移除）==========
                if (pairs.isNotEmpty()) {
                    item { ListHeader { Text("匹配结果（${pairs.size} 对）") } }
                    item {
                        Text(
                            "点 × 移除该对；确认后点「全部写入」",
                            fontSize = 8.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    itemsIndexed(pairs) { index, pair ->
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(0.94f),
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
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "🎵 ${pair.track.title}",
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = "🖼 ${pair.image.name}（重合 ${pair.score} 字符）",
                                        fontSize = 8.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "移除该对",
                                    tint = Color.Gray,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            pairs = pairs.filterIndexed { i, _ -> i != index }
                                        },
                                )
                            }
                        }
                    }
                    if (unmatchedImages.isNotEmpty()) {
                        item {
                            Text(
                                "未匹配图片 ${unmatchedImages.size} 张（不会写入）",
                                fontSize = 8.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                vm.writeCoverArtBatch(pairs.map { it.track to it.image.bytes })
                                pairs = emptyList()
                                unmatchedImages = emptyList()
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .padding(vertical = 4.dp),
                        ) {
                            Text("全部写入（${pairs.size}）", fontSize = 11.sp)
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                pendingTrack = null
                                showFilePicker = true
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .padding(vertical = 4.dp),
                        ) {
                            Text("重新选择图片", fontSize = 11.sp)
                        }
                    }
                }

                // ========== 单曲写入（已带封面的歌曲点击即为替换封面）==========
                item { ListHeader { Text("单曲写入 · 点击替换/添加封面") } }
                items(tracks.size) { i ->
                    val t = tracks[i]
                    val hasCover = t.id in coveredIds
                    Card(
                        onClick = {
                            pendingTrack = t
                            showFilePicker = true
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
                                onSuccess = { coveredIds = coveredIds + t.id },
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
                            // 已有封面 → 替换 / 无封面 → 添加
                            Text(
                                if (hasCover) "替换" else "添加",
                                fontSize = 9.sp,
                                color = if (hasCover) Color(0xFF98EAF9) else Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (hasCover) Color(0x2298EAF9) else Color(0x33FFFFFF))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        // 内置文件管理器（图片多选）浮层：替代系统照片选择器
        if (showFilePicker) {
            FilePickerScreen(
                onResult = { handlePicked(it) },
                onClose = { showFilePicker = false },
            )
        }
    }
}

// ---------------- 图片读取 / 匹配工具 ----------------

/** 读取选中图片的显示文件名（SAF） */
private fun queryDisplayName(
    context: android.content.Context,
    uri: android.net.Uri,
): String? {
    return try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
    } catch (_: Throwable) {
        null
    }
}

/** 解码图片 → 居中裁方 → 512px → JPEG 82% 压缩；失败返回 null */
private fun loadCoverBytes(
    context: android.content.Context,
    uri: android.net.Uri,
): ByteArray? {
    return try {
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null
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

/** 文件名去扩展名 + 去空白 + 转小写（匹配比较用） */
private fun normalizeName(name: String): String {
    val noExt = name.substringBeforeLast('.', name)
    return noExt.lowercase().filterNot { it.isWhitespace() }
}

/**
 * 两字符串的最长公共子串长度（滚动数组 DP，O(n·m)）。
 * ≥3 即认为「有重合」——封面工具自动匹配的判定阈值。
 */
private fun longestCommonSubstring(a: String, b: String): Int {
    if (a.length < 3 || b.length < 3) return 0
    val dp = IntArray(b.length + 1)
    var best = 0
    for (i in a.indices) {
        var prevDiag = 0
        for (j in 1..b.length) {
            val tmp = dp[j]
            dp[j] = if (a[i] == b[j - 1]) prevDiag + 1 else 0
            if (dp[j] > best) best = dp[j]
            prevDiag = tmp
        }
    }
    return best
}

/**
 * 自动匹配：曲目（歌名 title / 文件名 displayName 取最佳）↔ 图片文件名，
 * 最长公共子串 ≥ [MATCH_THRESHOLD] 字符即判定为对应封面。
 * 贪心分配：按重合字符数从高到低，一张图只配一首歌，一首歌只配一张图。
 *
 * @return (匹配对列表, 未匹配图片名列表)
 */
private fun matchCovers(
    tracks: List<Track>,
    images: List<PickedImage>,
): Pair<List<CoverPair>, List<String>> {
    data class Candidate(val trackIdx: Int, val imageIdx: Int, val score: Int)

    val candidates = mutableListOf<Candidate>()
    val normImages = images.map { normalizeName(it.name) }
    tracks.forEachIndexed { ti, t ->
        val normTitle = normalizeName(t.title)
        val normFile = normalizeName(t.displayName)
        normImages.forEachIndexed { ii, normImg ->
            val score = maxOf(
                longestCommonSubstring(normTitle, normImg),
                longestCommonSubstring(normFile, normImg),
            )
            if (score >= MATCH_THRESHOLD) {
                candidates.add(Candidate(ti, ii, score))
            }
        }
    }
    candidates.sortByDescending { it.score }
    val usedTracks = mutableSetOf<Int>()
    val usedImages = mutableSetOf<Int>()
    val pairs = mutableListOf<CoverPair>()
    for (c in candidates) {
        if (c.trackIdx in usedTracks || c.imageIdx in usedImages) continue
        usedTracks.add(c.trackIdx)
        usedImages.add(c.imageIdx)
        pairs.add(CoverPair(tracks[c.trackIdx], images[c.imageIdx], c.score))
    }
    val unmatched = images.mapIndexedNotNull { i, img ->
        if (i in usedImages) null else img.name
    }
    return pairs to unmatched
}

/** 文件名重合字符数达到该值即自动判定为对应封面 */
private const val MATCH_THRESHOLD = 3
