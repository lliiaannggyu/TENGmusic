package com.tengwear.tengmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.automirrored.filled.ArrowBack

private val CAT_BG = Color(0xF20B0B0E)

/** 归类列表的两种模式：播放（默认）/ 把当前歌加入类别（双击歌名唤起） */
enum class CategoryListMode { PLAY, ASSIGN }

/**
 * 归类列表（双击歌名 / 点底部「归类」键唤起）：
 *
 * - **播放模式**（点播放器底部「归类」键）：
 *   - 点击类别 = 播放该归类并回到播放器；归类解析不出曲目时停留并提示；
 *   - 长按不动 700ms 再松手 = 弹出删除确认（仅自定义类别可删）；
 *   - 长按后上下拖动 = 排序（跟手移动 + 目标高亮，松手才一次性交换，不闪跳）；
 *   - 「新增类别」按钮位于列表末尾，跟着列表一起滚动；
 *   - 列表容器为圆角胶囊（RoundedCornerShape 50%）。
 * - **归类模式**（双击播放器顶部歌名）：
 *   - 点击类别 = 把当前曲目加入该类别（收藏 = 收藏开关，已加入的再点一次 = 移出）；
 *   - 使用 Card 原生 onClick（不挂自定义手势，杜绝与卡片内部 clickable 抢事件导致"点击没反应"）；
 *   - 页面不自动退出：可连续加入多个类别，通过左上角返回键 / 系统返回退出；
 *   - 当前没有播放中的歌曲时给出明确提示（不再静默无反馈）。
 *
 * 组件颜色跟随主题强调色（歌词色：专辑主色或用户自定义色）。
 */
@Composable
fun CategoryListScreen(
    vm: PlayerViewModel,
    onClose: () -> Unit,
    mode: CategoryListMode = CategoryListMode.PLAY,
) {
    val cats by vm.categories.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val currentTrack by vm.currentTrack.collectAsState()
    val lyricColor by vm.lyricColor.collectAsState()
    val accent = Color(lyricColor)
    val listState = rememberScalingLazyListState()
    val fr = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var showNaming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var showDeleteId by remember { mutableStateOf<String?>(null) }
    val nameFr = remember { FocusRequester() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var longJob by remember { mutableStateOf<Job?>(null) }
    var rotaryAcc by remember { mutableStateOf(0f) }
    // 归类模式的操作反馈（短暂显示后自动消失；页面本身不自动退出）
    var assignMsg by remember { mutableStateOf<String?>(null) }
    // 消息自动清除（1.4s）；连续操作时后到的消息会重启计时
    LaunchedEffect(assignMsg) {
        if (assignMsg != null) {
            delay(1400)
            assignMsg = null
        }
    }

    LaunchedEffect(Unit) {
        try { fr.requestFocus() } catch (_: Throwable) {}
    }

    Box(Modifier.fillMaxSize().background(CAT_BG)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 标题栏：返回 + 标题（归类模式显示待加入的歌曲名）
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (mode == CategoryListMode.ASSIGN) "添加到归类" else "归类",
                        fontSize = 14.sp,
                        color = Color.White,
                    )
                    if (mode == CategoryListMode.ASSIGN) {
                        Text(
                            text = currentTrack?.title ?: "未在播放",
                            fontSize = 8.sp,
                            color = accent,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
            }

            // 列表（Wear M3E：ScalingLazyColumn + ListHeader + 胶囊 Card 行）
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
                    val isDragging = draggingId == cat.id
                    val isTarget = mode == CategoryListMode.PLAY &&
                        targetIndex == i && draggingId != null && draggingId != cat.id
                    // 归类模式：标记当前歌已在类别中的项（currentTrack 为响应式 StateFlow，
                    // 先取局部变量避免 delegated property 无法智能转换）
                    val ct = currentTrack
                    val inCat = mode == CategoryListMode.ASSIGN && ct != null &&
                        (cat.id == CategoryRepository.ID_FAV && favorites.contains(ct.id) ||
                            (!cat.isBuiltIn && cat.trackIds.contains(ct.id)))
                    Card(
                        // 两种模式的纯点击都走 Card 原生 onClick：与 ScalingLazyColumn 滚动
                        // 共存是框架标准路径，远比手写 awaitEachGesture 的 tap 判定可靠——
                        // 旧实现在卡片上手指移动超 8px 即判"滚动意图"放弃点击且无任何反馈，
                        // 手表上点 62px 高的小卡片极易误触（表现为"点了没播放，停在这页"）。
                        // pointerInput 只保留播放模式「长按之后」的拖动排序与松手删除。
                        onClick = if (mode == CategoryListMode.ASSIGN) {
                            {
                                val track = currentTrack
                                if (track == null) {
                                    // 未在播放（例如双击了"未选择音乐"标题）：明确提示，
                                    // 不再静默无反馈，也不关闭页面
                                    assignMsg = "当前没有播放中的歌曲"
                                } else {
                                    // 读实时状态，避免组合期快照过期
                                    val latest = vm.categories.value.firstOrNull { it.id == cat.id }
                                    val alreadyIn =
                                        (cat.id == CategoryRepository.ID_FAV &&
                                            vm.favorites.value.contains(track.id)) ||
                                        (latest != null && !latest.isBuiltIn &&
                                            latest.trackIds.contains(track.id))
                                    when {
                                        cat.id == CategoryRepository.ID_FAV -> {
                                            val wasFav = vm.favorites.value.contains(track.id)
                                            vm.toggleFavorite(track.id)
                                            assignMsg = if (wasFav) "已取消收藏" else "已收藏"
                                        }
                                        cat.isBuiltIn -> {
                                            // 全部歌曲：无需单曲加入
                                            assignMsg = "全部歌曲包含所有曲目"
                                        }
                                        alreadyIn -> {
                                            vm.removeTrackFromCategory(track.id, cat.id)
                                            assignMsg = "已移出：${cat.name}"
                                        }
                                        else -> {
                                            val ok = vm.addTrackToCategory(track.id, cat.id)
                                            assignMsg =
                                                if (ok) "已加入：${cat.name}" else "加入失败"
                                        }
                                    }
                                }
                            }
                        } else {
                            {
                                // 播放模式：点击类别 = 播放该归类
                                val ok = vm.playCategory(cat.id)
                                if (ok) onClose()
                                else assignMsg =
                                    if (vm.tracks.value.isEmpty()) "曲库扫描中，请稍候"
                                    else "该归类暂无歌曲"
                            }
                        },
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
                            .then(
                                if (isTarget) {
                                    Modifier.border(1.5.dp, accent, RoundedCornerShape(percent = 50))
                                } else {
                                    Modifier
                                }
                            )
                            .pointerInput(cat.id, mode) {
                                // 仅播放模式挂手势，且只处理「长按之后」的拖动排序与松手删除：
                                // 纯点击已由 Card 原生 onClick 播放（见上方），不在此判定——
                                // 手写 tap 的 8px 位移阈值在手表小卡片上误触率极高。
                                // 长按前的手指滑动不消费，放行给列表滚动。
                                if (mode == CategoryListMode.PLAY) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        // 不 consume down：保证未长按的滑动能正常滚动列表；
                                        // 只有「长按后的拖动」才 consume，把列表滚动压掉。
                                        val downY = down.position.y
                                        var longPressFired = false
                                        var dragMoved = false
                                        var movedEarly = false
                                        dragOffset = 0f
                                        targetIndex = null
                                        longJob?.cancel()
                                        // 长按计时：按住不动 700ms 才进入「可拖动/删除」态，
                                        // 避免"碰一下就拖"（滑一下即拖动）的误触。
                                        longJob = scope.launch {
                                            delay(700)
                                            if (!dragMoved && !movedEarly) {
                                                longPressFired = true
                                                draggingId = cat.id
                                            }
                                        }
                                        var lastY = downY
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes
                                                .firstOrNull { it.id == down.id } ?: break
                                            if (change.changedToUp()) {
                                                longJob?.cancel()
                                                when {
                                                    // 拖动排序结束：松手才一次性交换（跟手过程不改数据，杜绝闪跳）
                                                    dragMoved -> {
                                                        val ids = vm.categories.value.map { it.id }
                                                        val from = ids.indexOf(cat.id)
                                                        val to = targetIndex
                                                        if (from >= 0 && to != null && to != from &&
                                                            to in ids.indices
                                                        ) {
                                                            vm.reorderCategories(
                                                                ids.toMutableList()
                                                                    .also { it.add(to, it.removeAt(from)) }
                                                            )
                                                        }
                                                    }
                                                    // 长按后未移动就松手：弹出删除确认
                                                    longPressFired -> {
                                                        showDeleteId = cat.id
                                                    }
                                                    // 纯点击（已由 Card onClick 播放）/ 长按前滑动
                                                    // （滚动意图，放行列表）：此处不再动作
                                                    else -> {}
                                                }
                                                draggingId = null
                                                dragOffset = 0f
                                                targetIndex = null
                                                dragMoved = false
                                                break
                                            }
                                            val dy = change.position.y - lastY
                                            lastY = change.position.y
                                            if (longPressFired) {
                                                // 长按已触发：先消费全部移动，阻止列表滚动抢事件
                                                change.consume()
                                                if (!dragMoved && abs(dy) > 6f) dragMoved = true
                                            } else if (!dragMoved &&
                                                abs(change.position.y - downY) > 8f
                                            ) {
                                                // 长按前滑动：取消长按，标记为"提前滑动"，不排序
                                                movedEarly = true
                                                longJob?.cancel()
                                            }
                                            if (dragMoved) {
                                                // 跟手：只更新位移与目标高亮，不立刻改数据
                                                val ids = vm.categories.value.map { it.id }
                                                val from = ids.indexOf(cat.id)
                                                if (from >= 0) {
                                                    val info = listState.layoutInfo.visibleItemsInfo
                                                        .firstOrNull { it.index == from }
                                                    val itemH = info?.size?.toFloat() ?: 62f
                                                    // 位移夹取在邻居范围内，防止一次跳多格
                                                    val maxOff = (ids.size - 1 - from).coerceAtLeast(0) * itemH
                                                    val minOff = -from * itemH
                                                    dragOffset = (dragOffset + dy)
                                                        .coerceIn(minOff.toFloat(), maxOff.toFloat())
                                                    targetIndex =
                                                        (from + (dragOffset / itemH).roundToInt())
                                                            .coerceIn(0, ids.lastIndex)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        shape = RoundedCornerShape(percent = 50),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isDragging -> accent.copy(alpha = 0.22f)
                                isTarget -> accent.copy(alpha = 0.12f)
                                else -> CAT_BG
                            },
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
                                    color = if (isDragging) accent else Color.White,
                                )
                                Text(
                                    text = if (mode == CategoryListMode.ASSIGN) {
                                        if (inCat) "已在其中 · $count 首" else "$count 首"
                                    } else {
                                        "${if (cat.isBuiltIn) "内置" else "自定义"} · $count 首"
                                    },
                                    fontSize = 9.sp,
                                    color = if (inCat) accent else Color.Gray,
                                )
                            }
                            when {
                                mode == CategoryListMode.ASSIGN && inCat -> {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "已在其中",
                                        tint = accent,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                cat.id == CategoryRepository.ID_FAV -> {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Filled.LibraryMusic,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // 「新增类别」按钮：放在列表末尾，跟着列表一起滚动（两种模式都显示，
                // 归类模式下新建后可直接点它把当前歌加进去）
                item {
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
                item { Spacer(Modifier.height(8.dp)) }
            }

            // 操作反馈（底部短提示，1.4s 自消；归类/播放模式共用，如「该归类暂无歌曲」）
            val msg = assignMsg
            if (msg != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .background(accent.copy(alpha = 0.20f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = msg,
                        fontSize = 10.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
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
                        .background(Color(0xFF161B22), CircleShape)
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
                            "添加", fontSize = 12.sp, color = accent,
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
                        .background(Color(0xFF161B22), CircleShape)
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
