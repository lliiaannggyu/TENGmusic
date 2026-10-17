package com.tengwear.tengmusic.coverflow

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Initializes a CoverFlowState.
 *
 * @param onSelectHandler will be called with -1 while scrolling is taking place, and with the
 * selected index once an item is centered.
 * @param onItemClick will be called when a cover is tapped.
 */
@Composable
fun rememberCoverFlowState(
    onSelectHandler: (Int) -> Unit = {},
    onItemClick: (Int) -> Unit = {},
): CoverFlowState {
    val lazyListState: LazyListState = rememberLazyListState()
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    return remember {
        CoverFlowState(
            lazyListState,
            coroutineScope,
            onSelectHandler,
            onItemClick,
        )
    }
}

/**
 * Exposes the scroll state.
 */
class CoverFlowState internal constructor(
    internal val lazyListState: LazyListState,
    private val coroutineScope: CoroutineScope,
    private val onSelectHandler: (Int) -> Unit = {},
    internal val onItemClick: (Int) -> Unit = {},
) {
    internal var geometry: Geometry? = null

    /**
     * 平滑吸附 [AnimationSpec]：用「低回弹 + 中等刚度」的弹簧，
     * 比起原本的 medium-bouncy，回弹幅度被压成极轻微的一抹余韵，
     * 视觉上更"丝滑"而不是"q 弹"；同时刚度稍高使翻页响应更跟手。
     */
    private val bounceSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /**
     * 兜底曲线：当上一次滚动还没有完成时再次触发，覆盖弹簧动画自身不接续的问题，
     * 用 tween 平滑接力，避免出现"停一下再动"的卡顿。
     */
    private val fallbackSpec: AnimationSpec<Float> = tween(durationMillis = 260)

    /**
     * Index of the selected item. Set to -1 during scrolling.
     */
    var selectedIndex: Int = 0
        internal set(it) {
            field = it
            onSelectHandler(it)
        }

    /**
     * 计算把第 [listItemIndex] 个列表项（含首尾占位 spacer）滚动到容器正中所需的
     * 像素位移。列表布局完全确定：item0 / 末项 = spacerWidth，中间项 = coverOffset。
     */
    fun scrollDeltaToCenter(listItemIndex: Int): Float {
        val g = geometry ?: return 0f
        val layout = lazyListState.layoutInfo
        val itemCount = layout.totalItemsCount
        if (itemCount <= 2) return 0f
        val coverOffset = g.coverOffset
        val spacerWidth = g.spacerWidth
        val center = layout.viewportSize.width / 2f
        val sumW: (Int) -> Int = { k -> if (k <= 0) 0 else spacerWidth + coverOffset * (k - 1) }
        val first = lazyListState.firstVisibleItemIndex
        val currentScroll = sumW(first) + lazyListState.firstVisibleItemScrollOffset
        val w = if (listItemIndex == 0 || listItemIndex == itemCount - 1) spacerWidth else coverOffset
        return (sumW(listItemIndex) + w / 2f - center) - currentScroll
    }

    /**
     * 用更丝滑的低回弹弹簧把列表滚动指定像素（fling / 点击 / 表冠共用）。
     *
     * 关键修复：吸附动画必须**单实例**。此前每次调用都新起一个协程动画，快速连续
     * 操作（表冠连转 / fling 未结束又触发点击）时，两个 animateScrollBy 并发叠加，
     * 各自按"发起时的相对位移"滚动，最终停在两张封面中间——正是"卡在切换动画
     * 那里不回正"的真正根因。现在用 snapJob 取消上一次动画，保证同一时刻只有
     * 一个吸附动画在跑。
     */
    private var snapJob: Job? = null

    fun flingSnap(deltaPx: Float) {
        if (geometry == null || deltaPx == 0f) return
        snapJob?.cancel()
        snapJob = coroutineScope.launch {
            // 若上一次动画在跑，无缝切换为 tween，避免两次弹簧叠加产生的"卡顿"
            if (lazyListState.isScrollInProgress) {
                lazyListState.animateScrollBy(deltaPx, fallbackSpec)
            } else {
                lazyListState.animateScrollBy(deltaPx, bounceSpec)
            }
        }
    }

    /**
     * Animated scrolling to the item at index. Will be launched in a [CoroutineScope].
     */
    fun scrollToItem(
        index: Int,
    ) {
        flingSnap(scrollDeltaToCenter(index + 1))
    }
}
