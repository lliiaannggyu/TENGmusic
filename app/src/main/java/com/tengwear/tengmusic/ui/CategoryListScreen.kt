package com.tengwear.tengmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.tengwear.tengmusic.CategoryRepository
import com.tengwear.tengmusic.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.ArrowBack

private val CAT_BG = Color(0xF20B0B0E)
private val CAT_ACCENT = Color(0xFF34C7F4)

/**
 * 归类列表（双击歌名 / 点底部「归类」键唤起）：
 * - 内置「全部歌曲」「收藏」与用户自定义类别，显示各自歌曲数
 * - 点击类别：开始播放该归类，并回到播放器
 * - 底部加号：用系统键盘命名新增自定义类别
 * - 长按类别（按住不动）弹出删除确认（仅自定义类别可删）
 * - 拖动类别：长按后上下拖动排序，松手生效
 * - 支持旋转表冠滚动列表
 */
@Composable
fun CategoryListScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val cats by vm.categories.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val listState = rememberScalingLazyListState()
    val fr = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var showNaming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var showDeleteId by remember { mutableStateOf<String?>(null) }
    val nameFr = remember { FocusRequester() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }
    var longJob by remember { mutableStateOf<Job?>(null) }
    var rotaryAcc by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        try { fr.requestFocus() } catch (_: Throwable) {}
    }

    Box(Modifier.fillMaxSize().background(CAT_BG)) {
        Column(Modifier.fillMaxSize()) {
            // 标题栏：返回 + 标题
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回播放器",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp).clickable(onClick = onClose),
                )
                Text("归类", fontSize = 14.sp, color = Color.White)
                Spacer(Modifier.width(18.dp))
            }

            // 列表（Wear M3E：ScalingLazyColumn + ListHeader + Card 行）
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .focusRequester(fr)
                    .focusable()
                    .onRotaryScrollEvent { event ->
                        rotaryAcc += event.verticalScrollPixels
                        if (abs(rotaryAcc) >= 12f) {
                            listState.dispatchRawDelta(rotaryAcc * 0.5f)
                            rotaryAcc = 0f
                        }
                        true
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { ListHeader { Text("归类") } }
                items(cats.size) { i ->
                    val cat = cats[i]
                    val count = cat.count(tracks, favorites)
                    val isDragging = draggingIndex == i
                    Card(
                        // 点击交互全部由下方 awaitEachGesture 统一处理
                        // （轻点播放 / 长按删除 / 上下拖动排序），避免 clickable 与
                        // 拖拽手势互相抢事件导致"选了没反应"
                        onClick = {},
                        onLongClick = {},
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .then(
                                if (isDragging) {
                                    Modifier.graphicsLayer { translationY = dragOffset }
                                } else {
                                    Modifier
                                }
                            )
                            .pointerInput(cat.id) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    draggingIndex = i
                                    dragOffset = 0f
                                    dragMoved = false
                                    longJob?.cancel()
                                    longJob = scope.launch {
                                        delay(500)
                                        if (!dragMoved && draggingIndex == i) showDeleteId = cat.id
                                    }
                                    var lastY = down.position.y
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes
                                            .firstOrNull { it.id == down.id } ?: break
                                        if (change.changedToUp()) {
                                            longJob?.cancel()
                                            // 轻点（未拖动、未触发长按删除）→ 播放该归类。
                                            // 修复：旧实现用 detectDragGestures，其 onDragStart
                                            // 只在手指移动超过触摸阈值后才触发，纯点击根本
                                            // 走不到 onDragEnd 的播放分支——即"无法选择"。
                                            if (!dragMoved && showDeleteId == null) {
                                                vm.playCategory(cats[i].id)
                                                onClose()
                                            }
                                            draggingIndex = null
                                            dragOffset = 0f
                                            dragMoved = false
                                            break
                                        }
                                        val dy = change.position.y - lastY
                                        lastY = change.position.y
                                        if (!dragMoved && abs(dy) > 10f) {
                                            dragMoved = true
                                            longJob?.cancel()
                                        }
                                        if (dragMoved) {
                                            change.consume()
                                            dragOffset += dy
                                            val from = draggingIndex ?: continue
                                            val info = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.index == i }
                                            val itemH = (info?.size?.toFloat() ?: 60f)
                                            while (abs(dragOffset) > itemH / 2f) {
                                                val dir = if (dragOffset > 0f) 1 else -1
                                                val to = (from + dir).coerceIn(0, cats.lastIndex)
                                                if (to == from) { dragOffset = 0f; break }
                                                val newIds = vm.categories.value.map { it.id }
                                                    .toMutableList()
                                                    .also { it.add(to, it.removeAt(from)) }
                                                vm.reorderCategories(newIds)
                                                draggingIndex = to
                                                dragOffset -= dir * itemH
                                            }
                                        }
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDragging) Color(0x44234A5E) else Color(0xF20B0B0E),
                            contentColor = Color.White,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = cat.name,
                                    fontSize = 13.sp,
                                    color = if (isDragging) CAT_ACCENT else Color.White,
                                )
                                Text(
                                    text = "${if (cat.isBuiltIn) "内置" else "自定义"} · $count 首",
                                    fontSize = 9.sp,
                                    color = Color.Gray,
                                )
                            }
                            if (cat.isBuiltIn) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = CAT_ACCENT,
                                    modifier = Modifier.size(14.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Category,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // 底部加号：新增自定义类别（M3E Button）
            Button(
                onClick = {
                    name = ""
                    showNaming = true
                },
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .padding(vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("新增类别", fontSize = 11.sp)
            }
        }

        // 命名输入（唤起系统键盘）
        if (showNaming) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable(onClick = { showNaming = false }),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .wrapContentHeight()
                        .fillMaxWidth(0.86f)
                        .background(Color(0xFF161B22), RoundedCornerShape(20.dp))
                        .padding(14.dp),
                ) {
                    Text("新类别名称", fontSize = 12.sp, color = Color.White)
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                            .padding(8.dp)
                            .focusRequester(nameFr),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        singleLine = true,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Text(
                            "取消", fontSize = 12.sp, color = Color.Gray,
                            modifier = Modifier.clickable { showNaming = false },
                        )
                        Text(
                            "添加", fontSize = 12.sp, color = CAT_ACCENT,
                            modifier = Modifier.clickable {
                                vm.addCategory(name.trim())
                                showNaming = false
                                name = ""
                            },
                        )
                    }
                }
            }
            LaunchedEffect(showNaming) {
                if (showNaming) try { nameFr.requestFocus() } catch (_: Throwable) {}
            }
        }

        // 删除确认（仅自定义类别）
        if (showDeleteId != null) {
            val del = showDeleteId
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable(onClick = { showDeleteId = null }),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .wrapContentHeight()
                        .fillMaxWidth(0.86f)
                        .background(Color(0xFF161B22), RoundedCornerShape(20.dp))
                        .padding(14.dp),
                ) {
                    Text("删除该类别？", fontSize = 13.sp, color = Color.White)
                    Text("内置类别不可删", fontSize = 9.sp, color = Color.Gray)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Text(
                            "取消", fontSize = 12.sp, color = Color.Gray,
                            modifier = Modifier.clickable { showDeleteId = null },
                        )
                        Text(
                            "删除", fontSize = 12.sp, color = Color(0xFFFF5A7A),
                            modifier = Modifier.clickable {
                                if (del != null) vm.removeCategory(del)
                                showDeleteId = null
                            },
                        )
                    }
                }
            }
        }
    }
}
