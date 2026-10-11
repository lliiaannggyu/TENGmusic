package com.example.wearmusic.coverflow

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.min

/**
 * 以下 Cover Flow 实现移植自 GitHub 开源库 pakohan/compose-coverflow（MIT License）。
 * 原库地址：https://github.com/pakohan/compose-coverflow
 * 视觉/几何算法（rotationY / scale / translationX 随距中心距离计算 + 镜像倒影）原样保留，
 * 仅做了 Compose 版本适配（去掉 @Parcelize、把 1.7+ 的 rememberSnapFlingBehavior 重载换成 1.6 等效写法）。
 */

/**
 * Params for configuring the coverflow composable.
 *
 * All relevant values are relative to the dimensions of the CoverFlow composable to make it responsive.
 * It is based on the shorter of the two sides of the outer component.
 *
 * @param size is multiplied with that edge to get the cover size in the middle.
 * @param offset is multiplied with that edge to get the distance between two covers in the list.
 * @param distanceFactor calculates how strongly the transformations are applied to covers depending
 * on their distance to the center.
 * @param angle is the maximum rotation angle.
 * @param shift is the horizontal shift of the items away from the center. Useful for giving the
 * center element more space.
 * @param zoom makes the side elements smaller.
 * @param mirror activates the mirror (reflection) effect.
 */
@Immutable
data class CoverFlowParams(
    val size: Float = .5f,
    val offset: Float = .4f,
    val distanceFactor: DistanceFactor = OffsetLinearDistanceFactor(),
    val angle: Float = 55f,
    val shift: Float = .4f,
    val zoom: Float = .8f,
    val mirror: Boolean = true,
)

@Immutable
@Stable
internal class Geometry(
    internal val params: CoverFlowParams = CoverFlowParams(),
    private val size: IntSize = IntSize.Zero,
) {
    @Stable
    private val shortEdge
        get() = min(
            size.width,
            size.height,
        )

    @Stable
    private val containerCenter
        get() = size.width / 2

    @Stable
    private val zoomDelta
        get() = 1 - params.zoom

    @Stable
    internal val coverSize
        get() = shortEdge * params.size

    @Stable
    internal val coverOffset
        get() = (coverSize * params.offset).toInt()

    @Stable
    internal val spacerWidth
        get() = containerCenter - coverOffset / 2

    @Stable
    internal fun isSelected(horizontalPosition: Float): Boolean {
        return distanceToCenter(horizontalPosition).toInt() == 0
    }

    @Stable
    internal fun distanceToCenter(horizontalPosition: Float): Float {
        return horizontalPosition + coverOffset * .5f - containerCenter
    }

    @Stable
    internal fun effectFactor(distanceToCenter: Float): Float {
        return params.distanceFactor.factor(
            distanceToCenter / coverSize,
        )
    }

    @Stable
    internal fun rotation(distanceToCenter: Float): Float {
        return -params.angle * effectFactor(distanceToCenter)
    }

    @Stable
    internal fun scale(distanceToCenter: Float): Float {
        return 1 - zoomDelta * abs(effectFactor(distanceToCenter))
    }

    @Stable
    internal fun translationX(distanceToCenter: Float): Float {
        return coverOffset * params.shift * effectFactor(distanceToCenter)
    }
}

/**
 * DistanceFactor returns how strongly the three effects angle, horizontal translation, and zoom
 * get applied to each element in the view.
 */
interface DistanceFactor {
    /**
     * Factor should return negative values for all elements left of the center and positive for all
     * elements right of the center.
     *
     * * It is supposed to be 0 in the center.
     * * It can take values from -1 to 1.
     * * (`factor in -1f..1f`)
     * @param relativeDistance the distance to the center relative to focused covers size.
     */
    fun factor(
        relativeDistance: Float,
    ): Float
}

/**
 * DistanceFactor based on two values.
 * @param start determines from which distance the factor starts to increase.
 * @param end determines from which distance the factor is 1f.
 */
class OffsetLinearDistanceFactor(
    private val start: Float = 0f,
    private val end: Float = .5f,
) : DistanceFactor {

    init {
        if (start > end) {
            throw IllegalArgumentException("start ($start) must be smaller than end ($end)")
        }
    }

    /**
     * The factor grows from -1 to 0 to 1. The slope depends on the start and end parameters of the class.
     *
     * * `relativeDistance < -end -> -1f`
     * * `relativeDistance in -end..-start -> -1f..0f`
     * * `relativeDistance in -start..start -> 0f`
     * * `relativeDistance in start..end -> 0f..1f`
     * * `relativeDistance > end -> 1f`
     * * `if abs(relativeDistance) in start..end` the factor changes linear.
     */
    override fun factor(
        relativeDistance: Float,
    ): Float {
        // it's easier to only work with positive values (aka distances that are right of the center).
        val absolute = abs(relativeDistance)
        var factor = if (absolute <= start) {
            0f
        } else if (absolute >= end) {
            1f
        } else { // division by 0 not possible, since we don't enter if start == end
            val intervalStep = absolute - start
            val delta = end - start
            intervalStep / delta
        }

        if (relativeDistance < 0) {
            factor = -factor
        }

        return factor
    }
}

/**
 * mainly for debugging purposes
 */
class StaticDistanceFactor(private val factor: Float = 0f) : DistanceFactor {
    /**
     * Returns the negative factor for a negative distance and the positive factor for a positive
     * distance. 0 returns 0.
     */
    override fun factor(
        relativeDistance: Float,
    ): Float {
        return if (relativeDistance < 0) {
            -factor
        } else if (relativeDistance > 0) {
            factor
        } else {
            0f
        }
    }
}
