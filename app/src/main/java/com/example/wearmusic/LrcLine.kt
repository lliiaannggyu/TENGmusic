package com.example.wearmusic

/** 一行歌词：时间戳（毫秒）+ 文本 */
data class LrcLine(val timeMs: Long, val text: String)
