package com.example.wearmusic

import android.net.Uri

/**
 * 一张专辑（iPod 风格「专辑选曲」的浏览单位）。
 *
 * 设计要点：
 * - 按 albumId 分组，保证同一专辑的曲目聚合到一起，且专辑封面一致。
 * - trackIndices 保存曲目在全局曲目列表中的下标 —— 播放时直接把下标交给
 *   PlayerViewModel.selectTrack()，与「音乐库」页共用同一份播放队列。
 */
data class Album(
    val name: String,          // 专辑名
    val artist: String,        // 歌手（取专辑内首曲的歌手）
    val albumId: Long,         // 用于读取专辑封面
    val trackIndices: List<Int> // 该专辑曲目在全局列表中的下标
) {
    val trackCount: Int get() = trackIndices.size

    /** 专辑封面 Uri（无封面时 Coil 加载失败，由 UI 显示占位图） */
    val albumArtUri: Uri
        get() = Uri.parse("content://media/external/audio/albumart/$albumId")
}

/**
 * 把平铺的曲目列表按专辑分组。
 *
 * 分组键：有 albumId 时优先用 albumId（封面一致），否则退化用专辑名，
 * 避免扫描结果里 albumId 为 0 时全部被合并成一张「未知专辑」。
 */
fun groupTracksIntoAlbums(tracks: List<Track>): List<Album> {
    if (tracks.isEmpty()) return emptyList()

    val buckets = LinkedHashMap<String, MutableList<Int>>()
    tracks.forEachIndexed { i, t ->
        val key = if (t.albumId > 0L) "id:${t.albumId}" else "name:${t.album.trim().lowercase()}"
        buckets.getOrPut(key) { mutableListOf() }.add(i)
    }

    return buckets.values
        .mapNotNull { indices ->
            val first = tracks[indices.first()]
            Album(
                name = first.album.trim().ifEmpty { "未知专辑" },
                artist = first.artist.trim().ifEmpty { "未知歌手" },
                albumId = first.albumId,
                trackIndices = indices
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}
