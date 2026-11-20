package com.tengwear.tengmusic.coverflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import kotlin.math.abs

@Composable
internal fun Cover(
    geometry: Geometry,
    /** 叠放层级提示：由 content 下标与当前中心的「同步距离」计算，不依赖异步测量。
     *  回环平移/大跳转时 item 整组重建，重建首帧 onGloballyPositioned 还没回调，
     *  若沿用旧的「按 horizontalPosition 算 zIndex」，所有卡片初值都等于「在中心」，
     *  叠放顺序错乱一帧 —— 这正是「左右卡片一闪一闪」的根因。同步 hint 恒正确。 */
    zIndexHint: Float = 0f,
    onClickHandler: () -> Unit = {},
    onSelectedHandler: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    // 黑屏根因修复：原实现用 null 表示「还没测到坐标」，并据此跳过整个封面绘制，
    // 于是打开专辑流的前几帧所有封面都不画 => 整屏黑，卡在打开动画期间；
    // 等 onGloballyPositioned 回调后才集体亮起 => 就是「加载完才亮」。
    // 现在给一个合理的初始估算（列表项为 coverOffset 宽、中心在容器中心附近），
    // 封面从第一帧就绘制，测到真实坐标后再修正变换，期间始终可见。
    //
    // 性能关键（v1.60）：horizontalPosition 绝不能在组合阶段读取！滑动时每帧
    // onGloballyPositioned 都会写入该 state，若在组合期读取（旧代码在这里算
    // distanceToCenter 再传给参数式 graphicsLayer），每张可见卡片每帧整卡重组，
    // 快速滑动直接掉帧。现在 3D 变换全部放进 graphicsLayer 的 lambda —— 在
    // 绘制阶段读取 state，只触发重绘，滑动零重组。
    var horizontalPosition by remember {
        mutableStateOf<Float>(geometry.containerCenterForPosition - geometry.coverOffset / 2f)
    }

    // This box is used to space the elements horizontally and to give them the proper z index.
    Box(
        modifier = Modifier
            .width(with(LocalDensity.current) { Dp(geometry.coverOffset / LocalDensity.current.density) })
            .zIndex(zIndexHint)
            .onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInParent().x
                horizontalPosition = pos
                // isSelected 只在「哪张卡片越过中心」时变化，放在布局回调里判断
                // （组合阶段读取会引发每帧重组）；selectedIndex 是普通变量，
                // 布局期写入安全，不会产生重组循环。
                onSelectedHandler(geometry.isSelected(pos))
            },
        contentAlignment = Alignment.Center,
    ) {
        // This Box applies the transformation effects: rotation, horizontal shift, and zoom
        Box(
            modifier = Modifier
                .requiredSize(with(LocalDensity.current) { Dp(geometry.coverSize / LocalDensity.current.density) })
                .graphicsLayer {
                    // 绘制阶段读取 horizontalPosition：滑动每帧只重绘（改旋转/缩放），
                    // 不再触发任何重组 —— 这是滑动卡顿的根治点。
                    val d = geometry.distanceToCenter(horizontalPosition)
                    rotationY = geometry.rotation(d)
                    translationX = geometry.translationX(d)
                    scaleX = geometry.scale(d)
                    scaleY = geometry.scale(d)
                },
            propagateMinConstraints = true,
        ) {
                val interactionSource = remember { MutableInteractionSource() }
                if (geometry.params.mirror) {
                    Mirror(
                        modifier = Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClickHandler,
                        ),
                        coverSize = geometry.coverSize,
                    ) {
                        content()
                    }
                } else {
                    Box(
                        modifier = Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClickHandler,
                        ),
                    ) {
                        content()
                    }
                }
            }
    }
}
