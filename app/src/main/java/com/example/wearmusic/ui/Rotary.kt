package com.example.wearmusic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable

/**
 * 表冠（旋转输入）支持 —— 基于 Wear OS 官方 rotaryScrollable。
 *
 * Wear Compose 1.6.2 通过 [RotaryScrollableDefaults.snapBehavior] 提供「按项吸附」的表冠行为
 * （实体表冠 / 三星虚拟表冠通用），rotaryScrollable 内部用 FocusTargetWithSemantics
 * 自动抢占焦点，因此表冠事件稳定生效，无需再手写 requestFocus 也能接收。
 */

/**
 * 创建并返回 FocusRequester，供 [rotaryScroll] / [androidx.compose.ui.input.rotary.onRotaryScrollEvent] 使用
 * （官方 API 会据此自动抢占焦点）。
 *
 * @param active 仅当页面/浮层真正可见时才主动抢占焦点。横向翻页（播放 / 歌词 / 设置）三页同在组合树中，
 *   必须靠该开关保证「只有当前页」持有表冠焦点，否则上一页会一直偷走表冠事件。
 */
@Composable
fun rememberRotaryFocusRequester(active: Boolean = true): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(active) {
        if (active) {
            try {
                requester.requestFocus()
            } catch (_: Throwable) {
            }
        }
    }
    return requester
}

/** 让列表支持表冠滚动：基于 Wear OS 官方 rotaryScrollable（按项吸附，翻动歌词/列表）。 */
@Composable
fun Modifier.rotaryScroll(
    state: ScalingLazyListState,
    focusRequester: FocusRequester,
): Modifier {
    val behavior = RotaryScrollableDefaults.snapBehavior(state)
    return this.rotaryScrollable(behavior, focusRequester)
}
