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

/** 创建并返回 FocusRequester，供 [rotaryScroll] 使用（官方 API 会据此自动抢占焦点）。 */
@Composable
fun rememberRotaryFocusRequester(): FocusRequester {
    val requester = remember { FocusRequester() }
    // 兜底：显式请求一次焦点，确保进入页面即接收表冠事件（与官方内部抢占互补，不冲突）
    LaunchedEffect(Unit) {
        try {
            requester.requestFocus()
        } catch (_: Throwable) {
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
