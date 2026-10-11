package com.example.wearmusic.coverflow

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

/**
 * CoverFlow implements 🍎 CoverFlow for Jetpack Compose.
 *
 * It uses [LazyRow] under the hood.
 *
 * 移植自 pakohan/compose-coverflow（MIT）。Compose 1.6.7 自带的两个
 * `rememberSnapFlingBehavior` 重载都不暴露 snapAnimationSpec（默认无阻尼、无回弹），
 * 因此这里改用自写的 [bouncySnapFlingBehavior]：基于确定的列表布局精确算出吸附目标 +
 * 速度决定跨越张数，再用带回弹的弹簧滚动。几何/倒影逻辑原样保留。
 */
@Composable
fun CoverFlow(
    /** use it to change the background color */
    modifier: Modifier = Modifier,
    /** used to make the scrolling accessible to the outside */
    state: CoverFlowState = rememberCoverFlowState(),
    /** used to configure the design */
    params: CoverFlowParams = CoverFlowParams(),
    /** used to add list items in the [CoverFlowScope] */
    content: CoverFlowScope.() -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }

    LazyRow(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                size = coordinates.size
            },
        verticalAlignment = Alignment.CenterVertically,
        state = state.lazyListState,
        flingBehavior = remember(state) { bouncySnapFlingBehavior(state) },
    ) {
        if (size != IntSize.Zero) {
            val geometry = Geometry(
                params,
                size,
            )

            state.geometry = geometry

            val coverFlowScope = CoverFlowScopeImpl(
                geometry = geometry,
                lazyListScope = this,
                coverFlowState = state,
            )

            item {
                Spacer(modifier = Modifier.width(with(LocalDensity.current) { Dp(geometry.spacerWidth / LocalDensity.current.density) }))
            }

            coverFlowScope.apply(content)

            item {
                Spacer(modifier = Modifier.width(with(LocalDensity.current) { Dp(geometry.spacerWidth / LocalDensity.current.density) }))
            }
        }
    }
}

/**
 * 丝滑版吸附 [FlingBehavior]（替代 1.6.7 不暴露 snapAnimationSpec 的
 * `rememberSnapFlingBehavior`）。
 *
 * 思路：列表布局完全确定（item0 / 末项 = spacerWidth，其余 = coverOffset），
 * 因此能精确算出「把某一项滚到容器正中」的像素位移；松手时按速度估计要跨越的张数，
 * 为追求"丝滑"，将阈值放宽、跨越张数按速度曲线更柔顺地用对数压缩，
 * 最后用 [CoverFlowState.flingSnap] 的低回弹弹簧滚动,翻页连贯不颠簸。
 */
private fun bouncySnapFlingBehavior(state: CoverFlowState): FlingBehavior {
    return object : FlingBehavior {
        override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
            val g = state.geometry ?: return initialVelocity
            val layout = state.lazyListState.layoutInfo
            val itemCount = layout.totalItemsCount
            if (itemCount <= 2) return initialVelocity

            val coverOffset = g.coverOffset
            val spacerWidth = g.spacerWidth
            val center = layout.viewportSize.width / 2f
            val sumW: (Int) -> Int = { k -> if (k <= 0) 0 else spacerWidth + coverOffset * (k - 1) }

            // 当前已滚动的总像素（item0 左缘在 -currentScroll 处）
            val first = state.lazyListState.firstVisibleItemIndex
            val currentScroll = sumW(first) + state.lazyListState.firstVisibleItemScrollOffset

            // 找最靠近视口正中的项
            var nearest = 1
            var best = Float.POSITIVE_INFINITY
            for (i in 1 until itemCount - 1) {
                val w = if (i == 0 || i == itemCount - 1) spacerWidth else coverOffset
                val c = sumW(i) + w / 2f - currentScroll
                val d = abs(c - center)
                if (d < best) {
                    best = d
                    nearest = i
                }
            }

            // 丝滑版：阈值更宽、跨越张数用 sqrt 软化,
            // 弱甩慢回，强甩多页但增速递减,不会突然刷一大串
            val absV = abs(initialVelocity)
            val steps = when {
                absV < 250f -> 0
                else -> {
                    // sqrt 软化,1500 px/s 时约 1 张,3000 -> 2,6000 -> 3 ...
                    val raw = (Math.sqrt(absV / 1500.0)).toInt()
                    raw.coerceIn(0, 6).let { step ->
                        if (step == 0) (if (initialVelocity >= 0f) 1 else -1)
                        else if (initialVelocity < 0f) -step else step
                    }
                }
            }
            val target = (nearest + steps).coerceIn(1, itemCount - 2)

            state.flingSnap(state.scrollDeltaToCenter(target))
            return 0f
        }
    }
}
