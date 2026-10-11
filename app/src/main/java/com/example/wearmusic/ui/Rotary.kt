package com.example.wearmusic.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import kotlinx.coroutines.launch

/**
 * 表冠（旋转输入）支持。
 *
 * Wear Compose 1.3.1 未提供 rotary 组件，这里直接基于 compose-ui 的
 * Modifier.onRotaryScrollEvent（androidx.compose.ui.input.rotary）实现：
 * 把表冠的滚动像素映射到列表滚动状态上。
 */

/** 创建并在进入页面时自动请求焦点的 FocusRequester（表冠事件需要焦点才能接收） */
@Composable
fun rememberRotaryFocusRequester(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            requester.requestFocus()
        } catch (t: Throwable) {
            // 节点尚未附加时请求焦点会失败，忽略即可
        }
    }
    return requester
}

/** 让列表支持表冠滚动：需配合 rememberRotaryFocusRequester() 使用 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.rotaryScroll(
    state: ScalingLazyListState,
    focusRequester: FocusRequester
): Modifier {
    val scope = rememberCoroutineScope()
    return this
        .focusRequester(focusRequester)
        .focusable()
        .onRotaryScrollEvent { event ->
            scope.launch { state.scrollBy(event.verticalScrollPixels) }
            true
        }
}
