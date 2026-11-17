package com.tengwear.tengmusic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 完整挤压动画（v1.47）：点击（Press）瞬间播放一次**完整的**挤压脉冲 ——
 * 横向鼓起 22%、纵向收缩 10%，160ms 挤入 + 190ms 弹回（线性），
 * 不随松手提前结束（区别于 ButtonGroup.animateWidth 只在按压期间挤压、
 * 轻点一下就一闪即过的问题）。用于播放/切歌等所有播放器控件。
 */
@Composable
fun Modifier.pressPulse(interactionSource: InteractionSource): Modifier = composed {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                // 完整动画：不受松手影响，挤入 → 弹回走完全程
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(durationMillis = 160, easing = LinearEasing))
                pulse.animateTo(0f, tween(durationMillis = 190, easing = LinearEasing))
            }
        }
    }
    graphicsLayer {
        val p = pulse.value
        scaleX = 1f + 0.22f * p
        scaleY = 1f - 0.10f * p
    }
}
