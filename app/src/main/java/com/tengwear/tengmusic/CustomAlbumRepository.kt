package com.tengwear.tengmusic

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 自定义专辑（v1.47）：由用户手工创建的专辑 —— 可自行增加 / 删减歌曲、
 * 自定义封面、自定义排序（专辑与内部歌曲均可），全部持久化到本地 JSON。
 *
 * 结构：id（时间戳）、name（专辑名，可重名但 id 唯一）、coverUri（可空 = 默认音符图）、
 *       trackIds（按用户排定的歌曲顺序，存全局曲目 id）、orderIdx（专辑排序权重，越小越靠前）。
 */
data class CustomAlbum(
    val id: Long,
    val name: String,
    val coverUri: Uri?,
    val trackIds: List<Long>,
    val orderIdx: Int,
) {
    /** 专辑封面 Uri（无自定义封面时返回 null，UI 显示音符占位） */
    fun coverOr(albumId: Long): Uri =
        coverUri ?: Uri.parse("content://media/external/audio/albumart/$albumId")
}

object CustomAlbumRepository {

    private const val FILE = "custom_albums.json"

    /** 读取全部自定义专辑（按 orderIdx 升序） */
    fun load(context: Context): MutableList<CustomAlbum> {
        val out = mutableListOf<CustomAlbum>()
        return try {
            val f = File(context.filesDir, FILE)
            if (!f.exists()) return out
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ids = mutableListOf<Long>()
                val ia = o.optJSONArray("trackIds") ?: JSONArray()
                for (j in 0 until ia.length()) {
                    ia.optLong(j, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let { ids.add(it) }
                }
                out.add(
                    CustomAlbum(
                        id = o.optLong("id", 0L),
                        name = o.optString("name", "自定义专辑"),
                        coverUri = o.optString("coverUri", "").takeIf { it.isNotEmpty() }
                            ?.let { runCatching { Uri.parse(it) }.getOrNull() },
                        trackIds = ids,
                        orderIdx = o.optInt("orderIdx", i),
                    )
                )
            }
            out.sortBy { it.orderIdx }
            out
        } catch (_: Exception) {
            out
        }
    }

    /** 覆盖保存全部自定义专辑 */
    fun save(context: Context, albums: List<CustomAlbum>) {
        try {
            val arr = JSONArray()
            albums.forEach { a ->
                val o = JSONObject()
                o.put("id", a.id)
                o.put("name", a.name)
                o.put("coverUri", a.coverUri?.toString() ?: "")
                o.put("orderIdx", a.orderIdx)
                val ia = JSONArray()
                a.trackIds.forEach { ia.put(it) }
                o.put("trackIds", ia)
                arr.put(o)
            }
            File(context.filesDir, FILE).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }
}
