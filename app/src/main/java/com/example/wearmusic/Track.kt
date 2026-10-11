package com.example.wearmusic

import android.net.Uri

/** 一首本地音轨 */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,        // 用于读取专辑封面
    val duration: Long,        // 毫秒
    val displayName: String,   // 文件名（用于匹配 .lrc）
    val uri: Uri               // 内容提供者 Uri
) {
    /** 专辑封面 Uri（无封面时加载会失败，由 UI 显示占位图） */
    val albumArtUri: Uri
        get() = Uri.parse("content://media/external/audio/albumart/$albumId")
}
