package com.example.wearmusic.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

/** Wear OS 深色主题（圆形表盘友好） */
@Composable
fun WearMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
