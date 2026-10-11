package com.example.wearmusic

import android.content.Context

/** 收藏（喜欢）曲目 id 的本地持久化 */
object FavoritesRepository {
    private const val PREF = "wearmusic_fav"
    private const val KEY_IDS = "ids"

    fun load(context: Context): Set<Long> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun save(context: Context, ids: Set<Long>) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_IDS, ids.map { it.toString() }.toSet())
            .apply()
    }

    /** 切换某曲目的收藏状态，返回切换后的全集 */
    fun toggle(context: Context, trackId: Long): Set<Long> {
        val next = load(context).toMutableSet()
        if (!next.add(trackId)) next.remove(trackId)
        save(context, next)
        return next
    }
}
