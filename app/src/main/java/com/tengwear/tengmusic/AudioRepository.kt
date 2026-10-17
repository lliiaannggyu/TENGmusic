package com.tengwear.tengmusic

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

/** 通过 MediaStore 读取设备上的本地音乐（IS_MUSIC 且时长 > 30s） */
object AudioRepository {

    fun loadTracks(context: Context): List<Track> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} = ? AND ${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf("1", "30000")

        val list = mutableListOf<Track>()
        context.contentResolver.query(
            uri, projection, selection, selectionArgs, "lower(${MediaStore.Audio.Media.TITLE}) ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                list.add(
                    Track(
                        id = id,
                        title = c.getString(titleCol) ?: "未知",
                        artist = c.getString(artistCol) ?: "未知",
                        album = c.getString(albumCol) ?: "",
                        albumId = c.getLong(albumIdCol),
                        duration = c.getLong(durCol),
                        displayName = c.getString(nameCol) ?: "$id",
                        uri = ContentUris.withAppendedId(uri, id)
                    )
                )
            }
        }
        return list
    }
}
