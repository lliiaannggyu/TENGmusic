package com.tengwear.tengmusic.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.compose.foundation.Image
import com.tengwear.tengmusic.Album
import com.tengwear.tengmusic.PlayerViewModel
import com.tengwear.tengmusic.coverflow.CoverCache
import com.tengwear.tengmusic.coverflow.CoverFlow
import com.tengwear.tengmusic.coverflow.CoverFlowParams
import com.tengwear.tengmusic.coverflow.CoverFlowState
import com.tengwear.tengmusic.coverflow.rememberCoverFlowState
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * 专辑选曲页（iPod Classic「Cover Flow」，v1.47 起按专辑浏览）：
 * 一张封面 = 一张专辑（不再按单曲平铺）。
 *
 * 交互：
 *  - 表冠旋转 → 每咔哒一格翻一张专辑
 *  - 轻触侧面封面 → 滚到正中；轻触已居中的封面 → 打开该专辑的歌曲列表
 *  - 下滑屏幕 → 返回播放页
 *  - 打开动画：先定位到当前播放的专辑，两侧专辑从内向外依次展开（线性）
 *
 * 表冠焦点：项目统一 rememberRotaryFocusRequester(active)（详见历史注释）。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AlbumCoverFlow(
    vm: PlayerViewModel,
    modifier: Modifier = Modifier,
    sizeFraction: Float = 0.5f,
    onBack: () -> Unit = {},
    onOpenAlbum: (Album) -> Unit = {},
    active: Boolean = true,
) {
    val tracks by vm.tracks.collectAsState()
    val albums by vm.albums.collectAsState()
    val index by vm.currentIndex.collectAsState()
    val context = LocalContext.current

    // ===== 环形专辑流 =====
    // 在真实专辑前后各拼接若干张克隆，第一张左边能看到最后一张、最后一张右边能看到第一张。
    // clone 数量随专辑数自适应；只有 1 张专辑时不克隆。
    val albumCount = albums.size
    // 真正无缝循环：前后各复制 REP 组（不是 1 组）。
    // 缓冲越厚，滑动惯性再大也不会撞到物理头尾的 spacer；一旦中心滑进最外侧那组，
    // 就瞬时平移回中间真实区（内容是同一批专辑、位置完全对齐），用户全程看不到接缝。
    val rep = 3
    val clone = if (albumCount > 1) albumCount * rep else albumCount
    val contentCount = clone + albumCount + clone
    /** content 下标 → 真实专辑下标（统一取模，负数正确回绕） */
    fun realOf(contentIdx: Int): Int {
        if (albumCount <= 0) return 0
        val r = (contentIdx - clone) % albumCount
        return if (r < 0) r + albumCount else r
    }
    // 供 remember state 的点击回调读取最新值（state 只 remember 一次，闭包不能捕获旧值）
    val albumsHolder = remember { mutableStateOf(albums) }
    albumsHolder.value = albums
    val cloneHolder = remember { mutableIntStateOf(clone) }
    cloneHolder.value = clone
    val contentCountHolder = remember { mutableIntStateOf(contentCount) }
    contentCountHolder.value = contentCount

    // 最靠近屏幕中心的「content 下标」（真实专辑 = realOf(content 下标)）
    val centeredIndex = remember { mutableStateOf(clone) }
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val buzzMs by vm.flowBuzzMs.collectAsState()
    val buzzStrength by vm.flowBuzzStrength.collectAsState()
    val buzzMsHolder = remember { mutableIntStateOf(12) }
    buzzMsHolder.value = buzzMs
    val buzzAmpHolder = remember { mutableIntStateOf(255) }
    buzzAmpHolder.value = (255 * buzzStrength / 100).coerceIn(1, 255)
    val buzzSuppressUntil = remember { mutableStateOf(0L) }

    // 只有在「首次打开、动画播放期间」才按距离算渐显；动画结束后恒为 1，
    // 彻底杜绝「一圈有封面一圈黑白 / 滑动闪卡」——那些都是透明度长期依赖固定
    // startAlbumIdx 距离、而循环列表 content 下标会不断平移导致的。
    val animActive = remember { mutableStateOf(true) }
    // 动画进度用普通 State 承载：每帧写入会触发重组，保证渐显逐帧刷新
    // （Animatable.value 不是 State，直接读不会触发重组，之前动画"时有时无"就源于此）
    val animProgress = remember { mutableFloatStateOf(0f) }
    // 展开完成后的回弹量（0→+3.5%→0）：所有卡片 scale 乘 (1+bounce)，作为展开收尾
    val bounceScale = remember { mutableFloatStateOf(0f) }
    // 定位/回环跳转期间抑制「兜底归位」，否则会把刚定位好的列表又拽回去
    val suppressSnapUntil = remember { mutableStateOf(0L) }

    // 首次定位+展开动画只允许执行一次：此前 LaunchedEffect(active, albums.size) 在
    // 「从专辑详情返回专辑流」时也会重新触发 —— 把用户硬拽回当前播放专辑并重播
    // 520ms 定位动画，正是「动画乱跳/卡顿」的来源。现在用标记保证一次会话只播一次
    // （浮层整体关闭时组合销毁、remember 重置，下次打开仍会正常播放）。
    val didInitialPlacement = remember { mutableStateOf(false) }
    // 展开动画的距离锚点（v1.47 原版语义）：打开时定位到的 content 下标。
    // dist 按这个固定锚点算，不跟随滑动 —— 原版「从中间向两边展开」的层次感来源。
    val startContentIdx = remember { mutableIntStateOf(0) }
    // 展开动画是否已真正开始：浮层从底部滑入约 300~400ms，此前若直接播展开动画，
    // 中间两张（错峰最早）的 109ms 弹出窗口会被滑入过程完全盖住 —— 用户看到的就是
    // 「中间的没动画，远处的有」。现在分两段：滑入期间中心专辑全尺寸可见（跟随页面
    // 一起滑上来），滑入完成后才开始「中心先收后弹 + 两侧依次炸开」的展开动画。
    val animStarted = remember { mutableStateOf(false) }

    var state: CoverFlowState? = null
    state = rememberCoverFlowState(
        onItemClick = { idx ->
            val s = state
            if (s != null && idx == centeredIndex.value) {
                // 点「已居中」的封面 → 打开它对应的真实专辑
                val real = realOf(idx)
                albumsHolder.value.getOrNull(real)?.let(onOpenAlbum)
            } else {
                s?.scrollToItem(idx)
            }
        }
    )

    val focusRequester = rememberRotaryFocusRequester(active)

    val density = LocalDensity.current
    val snapTolerancePx = with(density) { 2.dp.toPx() }

    LaunchedEffect(state) {
        val s = state ?: return@LaunchedEffect
        val listState = s.lazyListState
        var stillFrames = 0
        while (true) {
            val total = contentCountHolder.value
            val cl = cloneHolder.value
            val ac = albumsHolder.value.size
            if (total <= 0 || ac <= 0) { delay(60); continue }
            // 一次 layoutInfo 遍历同时求出中心下标与像素偏移：此前两个函数各自访问
            // layoutInfo（每次访问都分配可见项快照），滑动中每 40ms 多一倍对象分配
            // 加重 GC —— GC 暂停同样会卡音频线程。
            val snap = centeredSnapshot(listState)
            val newContent = if (snap.first < 0) 0 else (snap.first - 1).coerceIn(0, total - 1)
            if (newContent != centeredIndex.value) {
                centeredIndex.value = newContent
                if (System.currentTimeMillis() >= buzzSuppressUntil.value) {
                    buzz(vibrator, buzzMsHolder.value, buzzAmpHolder.value)
                }
            }
            // ===== 真正无缝循环：中心一旦越过「中间真实区」，整表平移整数个周期 =====
            // 中间真实区 content ∈ [cl, cl+ac)；前后各有 cl 张克隆。
            // 当中心滑到真实区左侧相邻那一组 ([cl-ac, cl)) → 平移 +ac；
            // 滑到右侧相邻那一组 ([cl+ac, cl+2ac)) → 平移 -ac。
            // 平移 ac 张后指向的是「同一张专辑的克隆」，画面完全一样，但列表位置重回中间，
            // 于是两侧又各有整组克隆缓冲，物理头尾的 spacer 永远碰不到。
            // 关键：不要求 isScrollInProgress==false —— 滑动中也要回环，否则惯性大时
            // 会先撞到物理边界再回弹，看起来就是「到头了跳回第一张」。
            // 回环触发带：允许中心在「真实区 + 两侧各一组克隆」内自由滑动，
            // 只有当中心滑进更外侧那组（fast approaching 物理头尾）时才瞬时拉回中间。
            // 带两侧各留 ac 张缓冲，滑动惯性再大也碰不到 spacer，手感顺滑不打断。
            val loopLow = cl - ac      // 左侧缓冲带下限
            val loopHigh = cl + 2 * ac // 右侧缓冲带上限
            if (ac > 1 && System.currentTimeMillis() >= suppressSnapUntil.value &&
                (newContent < loopLow || newContent >= loopHigh)
            ) {
                // 平移整数个周期，把当前专辑锚回中间真实区 [cl, cl+ac)。
                // 内容 identical（同一张专辑的克隆），画面无跳变。
                val real = ((newContent - cl) % ac + ac) % ac
                val jumpTo = cl + real
                if (jumpTo in 0 until total && jumpTo != newContent) {
                    suppressSnapUntil.value = System.currentTimeMillis() + 220
                    buzzSuppressUntil.value = System.currentTimeMillis() + 220
                    centeredIndex.value = jumpTo
                    s.snapInstant(jumpTo)
                    stillFrames = 0
                    delay(40)
                    continue
                }
            }
            val offPx = snap.second
            if (!listState.isScrollInProgress && abs(offPx) > snapTolerancePx &&
                System.currentTimeMillis() >= suppressSnapUntil.value
            ) {
                stillFrames++
                // 30ms 轮询 + 3 帧阈值 ≈ 停止后 90ms 内吸附归位；
                // 此前 60ms×8 帧 ≈ 0.5s 才吸附，表冠翻页后要"卡"半秒才回正。
                if (stillFrames >= 2) {
                    s.scrollToItem(centeredIndex.value)
                    stillFrames = 0
                }
            } else {
                stillFrames = 0
            }
            // 低性能设备优化：静止时轮询降到 120ms（原来恒定 30ms，即使不滑动也
            // 每 30ms 遍历 visibleItems 唤醒 CPU，长此以往抢占音频线程）；
            // 滑动中保持 40ms 高频以保证回环/吸附跟手。静止时 offPx 基本为 0，
            // 不需要高频检查，120ms 足够检测表冠的一次翻页并立刻切回高频。
            delay(if (listState.isScrollInProgress) 40L else 120L)
        }
    }

    // 进入专辑流：先定位到「当前正在播放」的专辑，然后播放从内向外展开动画
    LaunchedEffect(active, albums.size) {
        if (!active || albums.isEmpty()) return@LaunchedEffect
        // 从专辑详情返回（active 再次变 true）绝不重新定位/重播动画，
        // 用户停在哪张专辑就留在哪张 —— 否则就是"返回时突然动画乱跳"。
        if (didInitialPlacement.value) return@LaunchedEffect
        didInitialPlacement.value = true
        var tries = 0
        while (state?.geometry == null && tries < 100) { delay(16); tries++ }
        // 当前播放曲目所属专辑的真实下标（找不到 → 0）
        val curReal = if (index in 0..tracks.lastIndex) {
            albums.indexOfFirst { index in it.trackIndices }.coerceAtLeast(0)
        } else 0
        // 真实下标 → content 下标（落在中间的真实区，前后都有克隆环绕）
        val curContent = cloneHolder.value + curReal
        centeredIndex.value = curContent
        startContentIdx.value = curContent
        buzzSuppressUntil.value = System.currentTimeMillis() + 900
        suppressSnapUntil.value = System.currentTimeMillis() + 900
        // 瞬时定位到当前专辑：绝不用动画滚动（否则打开瞬间会看到一串封面飞过＝像加载）
        state?.snapInstant(curContent)
        // 展开动画与浮层滑入交叠（v1.62）：浮层匀速滑入 300ms，滑到 3/5 处（180ms）
        // 就开始展开 —— 两者交叠 120ms，衔接连贯无停顿；中心专辑始终全尺寸可见。
        animActive.value = true
        animStarted.value = false
        animProgress.value = 0f
        delay(180)
        animStarted.value = true
        // 用 animate 逐帧写入普通 State（每帧触发重组，渐显才连续不卡）
        animate(0f, 1f, animationSpec = tween(durationMillis = 600, easing = LinearEasing)) { value, _ ->
            animProgress.value = value
        }
        animProgress.value = 1f
        animActive.value = false
        // 展开完成后的左右回弹：bounce 从 0 过冲到 1 再归 0（220ms），两侧卡片按
        // 各自方位沿水平方向弹开 10dp 再收回，作为展开的收尾（中心卡片不动）。
        animate(
            0f, 1f,
            animationSpec = keyframes {
                durationMillis = 220
                1f at 90
                0f at 220
            },
        ) { value, _ ->
            bounceScale.value = value
        }
        bounceScale.value = 0f
    }

    // 进入专辑流即后台预解码全部封面进 LruCache：
    // 之后克隆区/回环/快速滑动渲染的永远都是同一张内存位图，无任何异步加载中间态。
    LaunchedEffect(albums) {
        if (albums.isNotEmpty()) CoverCache.preloadAll(context, albums)
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFF06070A)),
        contentAlignment = Alignment.Center
    ) {
        if (albums.isEmpty()) {
            Text(
                "暂无专辑\n授权后自动扫描本地音乐",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            return@Box
        }

        var dragDownAcc by remember { mutableFloatStateOf(0f) }

        val listState = state?.lazyListState

        val crownSens by vm.crownSens.collectAsState()
        val crownFling by vm.crownFling.collectAsState()
        val sensHolder = remember { mutableFloatStateOf(1f) }
        sensHolder.value = crownSens
        val flingHolder = remember { mutableFloatStateOf(1f) }
        flingHolder.value = crownFling
        val decay = rememberSplineBasedDecay<Float>()
        val scaledFling = remember(decay) {
            object : FlingBehavior {
                override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                    val v0 = initialVelocity * flingHolder.value
                    if (abs(v0) <= 50f) return v0
                    var lastValue = 0f
                    AnimationState(initialValue = 0f, initialVelocity = v0).animateDecay(decay) {
                        val delta = value - lastValue
                        lastValue = value
                        val consumed = scrollBy(delta)
                        if (abs(delta - consumed) > 0.5f) cancelAnimation()
                    }
                    return lastValue
                }
            }
        }
        val scaledListState = listState?.let { ls ->
            rememberScrollableState { delta -> ls.dispatchRawDelta(delta * sensHolder.value) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .let { m ->
                    if (scaledListState != null) {
                        m.rotaryScrollable(
                            RotaryScrollableDefaults.behavior(
                                scrollableState = scaledListState,
                                flingBehavior = scaledFling,
                                hapticFeedbackEnabled = false,
                            ),
                            focusRequester,
                        )
                    } else {
                        m
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragDownAcc = 0f },
                        onDragEnd = {
                            val threshold = with(density) { 60.dp.toPx() }
                            if (dragDownAcc >= threshold) onBack()
                        },
                    ) { _, dragAmount -> dragDownAcc += dragAmount }
                }
        ) {
            CoverFlow(
                state = state,
                modifier = Modifier.fillMaxSize(),
                params = CoverFlowParams(
                    size = sizeFraction,
                    offset = 0.4f,
                    angle = 55f,
                    shift = 0.4f,
                    zoom = 0.8f,
                    mirror = true,
                ),
            ) {
                items(
                    contentCount,
                    key = { it },
                    // 叠放层级用「content 下标与中心的同步距离」计算：不依赖异步测量，
                    // 回环平移整组重建的首帧叠放顺序也正确 —— 根治左右卡片闪一下。
                    zIndexHint = { c ->
                        1f - (abs(c - centeredIndex.value).toFloat().coerceAtMost(10f)) * 0.01f
                    },
                ) { c ->
                    val real = realOf(c)
                    // 两段式展开渲染（v1.60 性能版）：
                    // alpha/scale 的计算全部放进 graphicsLayer 的 lambda —— 在绘制
                    // 阶段读取 animActive/animProgress 等 state，动画每帧只重绘、
                    // 不重组（此前在组合期算好再传参，动画期间每帧所有可见卡片
                    // 整卡重组，滑动叠加时直接掉帧）。防闪黑白逻辑不变：
                    // 动画结束后 lambda 短路恒 1，滑动/回环不再有透明度变化。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val active = animActive.value
                                val started = animStarted.value
                                val start = startContentIdx.value
                                val isCenter = c == start
                                val appear = when {
                                    !active -> 1f
                                    isCenter -> 1f
                                    !started -> 0f
                                    else -> {
                                        val dist = abs(c - start).toFloat()
                                        (animProgress.value * 5.5f - dist).coerceIn(0f, 1f)
                                    }
                                }
                                val sc = if (!active || isCenter) 1f else 0.45f + 0.55f * appear
                                alpha = appear
                                scaleX = sc
                                scaleY = sc
                                // 左右回弹（v1.62）：展开完成后，两侧卡片沿水平方向
                                // 向外弹一下再归位（左侧往左、右侧往右），中心卡片不动。
                                // 此前误做成全屏缩放过冲，已纠正。
                                val bounce = bounceScale.value
                                if (!isCenter && bounce > 0f) {
                                    val dir = if (c < startContentIdx.value) -1f else 1f
                                    translationX = dir * bounce * 10.dp.toPx()
                                }
                            }
                    ) {
                        albums.getOrNull(real)?.let { AlbumArtContent(album = it) }
                    }
                }
            }
        }

        // 底部居中：当前专辑名 / 歌手 / 曲目数（content 下标 → 真实专辑）
        val currentAlbum = albums.getOrNull(realOf(centeredIndex.value))
        val nameShadow = Shadow(color = Color.Black, offset = Offset(0f, 2f), blurRadius = 4f)
        val isNowPlaying = currentAlbum != null && index in 0..tracks.lastIndex &&
            index in currentAlbum.trackIndices

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp, start = 10.dp, end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentAlbum?.name ?: "",
                    fontSize = 14.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    style = TextStyle(shadow = nameShadow),
                )
                if (isNowPlaying) {
                    Spacer(Modifier.width(5.dp))
                    PlayingWave(color = Color(0xFF8FD8FF))
                }
            }
            Text(
                text = currentAlbum?.artist ?: "",
                fontSize = 10.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                color = Color.White,
                style = TextStyle(shadow = nameShadow),
            )
            Text(
                text = currentAlbum?.let { "${it.trackCount} 首 · 点按打开" } ?: "",
                fontSize = 9.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                color = Color(0xAAFFFFFF),
                style = TextStyle(shadow = nameShadow),
            )
        }
    }
}

/** 触发一次短促马达震动（时长/强度可在设置页自定义，默认 12ms / 默认强度） */
@Suppress("DEPRECATION")
private fun buzz(vibrator: Vibrator?, durationMs: Int, amplitude: Int) {
    if (vibrator == null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), amplitude))
    } else {
        vibrator.vibrate(durationMs.toLong())
    }
}

/** 一次 layoutInfo 遍历同时求出：最靠近视口中心的项下标（-1 = 首尾 spacer）
 *  和它相对视口中心的像素偏移（兜底归中判断用）。 */
private fun centeredSnapshot(listState: LazyListState): Pair<Int, Float> {
    val layout = listState.layoutInfo
    val viewportCenter = layout.viewportSize.width / 2f
    var best = -1
    var bestDist = Float.MAX_VALUE
    var bestOff = 0f
    for (item in layout.visibleItemsInfo) {
        val center = item.offset + item.size / 2f
        val d = abs(center - viewportCenter)
        if (d < bestDist) {
            bestDist = d
            best = item.index
            bestOff = center - viewportCenter
        }
    }
    return Pair(best, bestOff)
}

/** 专辑封面内容：铺满方形封面 + 轻微边框（无封面显示音符占位） */
@Composable
private fun AlbumArtContent(album: Album) {
    val context = LocalContext.current
    // 根治「循环一圈黑白 / 边缘闪卡」：不再用 AsyncImage 异步加载（复用/回环换 model
    // 的首帧必闪占位），改为直接画缓存中的位图 —— 同步、同帧、克隆共享同一张位图。
    var bmp by remember(album.albumId, album.customCoverUri) {
        mutableStateOf(CoverCache.get(album))
    }
    LaunchedEffect(album.albumId, album.customCoverUri) {
        if (bmp == null) {
            // 串行现场解码（Mutex 串行化）：滑动中多张未缓存卡片同时并发解码会
            // 打满 CPU 小核、音频线程被饿死 → 一滑动音乐就断续。串行后峰值减半。
            CoverCache.decodeLive(context, album)?.let {
                CoverCache.put(album, it)
                bmp = it
            }
        }
    }
    val bitmap = bmp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF161616))
            .border(1.dp, Color(0x33FFFFFF))
    ) {
        Icon(
            imageVector = Icons.Filled.Album,
            contentDescription = null,
            tint = Color(0x55FFFFFF),
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.Center),
        )
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = album.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** 「正在播放」声波标识：三根圆头声波柱，高度此消彼长地无限起伏 */
@Composable
private fun PlayingWave(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    val transition = rememberInfiniteTransition(label = "playing-wave")
    val h1 by transition.animateFloat(
        0.30f, 1.00f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "wave-bar-1",
    )
    val h2 by transition.animateFloat(
        1.00f, 0.35f,
        animationSpec = infiniteRepeatable(tween(460, easing = LinearEasing), RepeatMode.Reverse),
        label = "wave-bar-2",
    )
    val h3 by transition.animateFloat(
        0.45f, 0.95f,
        animationSpec = infiniteRepeatable(tween(340, easing = LinearEasing), RepeatMode.Reverse),
        label = "wave-bar-3",
    )
    Canvas(modifier = modifier.size(width = 15.dp, height = 11.dp)) {
        val barW = size.width * 0.20f
        val gap = (size.width - barW * 3f) / 2f
        val radius = CornerRadius(barW / 2f)
        listOf(h1, h2, h3).forEachIndexed { i, p ->
            val h = (size.height * p).coerceAtLeast(barW)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barW + gap), (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = radius,
            )
        }
    }
}
