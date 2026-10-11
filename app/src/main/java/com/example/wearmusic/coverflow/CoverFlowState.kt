package com.example.wearmusic.coverflow

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
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
     * 带回弹（overshoot）的吸附动画 spec：中等阻尼比 + 中等刚度，
     * 松手/点击后会轻微冲过中心再弹回，避免「生硬急停」。
     */
    private val bounceSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

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

    /** 用带回弹的弹簧把列表滚动指定像素（fling / 点击 / 表冠共用）。 */
    fun flingSnap(deltaPx: Float) {
        if (geometry == null || deltaPx == 0f) return
        coroutineScope.launch {
            lazyListState.animateScrollBy(deltaPx, bounceSpec)
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
