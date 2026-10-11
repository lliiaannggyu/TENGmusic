package com.example.wearmusic

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

/** 根据音轨文件名查找同名 .lrc，并解析为歌词行 */
object LyricsRepository {

    fun loadLyrics(context: Context, track: Track): List<LrcLine> {
        val base = track.displayName.substringBeforeLast('.')
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf("$base.lrc")

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                val lrcUri = ContentUris.withAppendedId(uri, id)
                runCatching {
                    context.contentResolver.openInputStream(lrcUri)?.use { s ->
                        return LrcParser.parse(s.bufferedReader().readText())
                    }
                }
            }
        }
        return emptyList()
    }
}
