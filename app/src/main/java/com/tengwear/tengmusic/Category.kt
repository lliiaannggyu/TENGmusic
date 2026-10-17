package com.tengwear.tengmusic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 播放归类（智能列表）：
 * - [TYPE_ALL] 全部歌曲（内置，不可删）
 * - [TYPE_FAVORITE] 收藏（内置，不可删）
 * - [TYPE_CUSTOM] 用户自定义类别（可命名、可删除；trackIds 为空时降级播放全部）
 */
data class Category(
    val id: String,
    val name: String,
    val type: Int,
    val order: Int,
    val trackIds: List<Long> = emptyList(),
) {
    val isBuiltIn: Boolean get() = type == CategoryRepository.TYPE_ALL || type == CategoryRepository.TYPE_FAVORITE

    /** 该归类包含的歌曲数（用于列表展示） */
    fun count(tracks: List<Track>, favorites: Set<Long>): Int =
        resolveTracks(tracks, favorites).size

    /** 解析该归类下的实际曲目（保持全局曲目顺序） */
    fun resolveTracks(tracks: List<Track>, favorites: Set<Long>): List<Track> = when (type) {
        CategoryRepository.TYPE_ALL -> tracks
        CategoryRepository.TYPE_FAVORITE -> tracks.filter { favorites.contains(it.id) }
        else -> if (trackIds.isEmpty()) tracks else tracks.filter { trackIds.contains(it.id) }
    }
}

/** 归类持久化：SharedPreferences + JSON 数组。保证内置「全部歌曲」「收藏」始终存在。 */
object CategoryRepository {
    const val TYPE_ALL = 0
    const val TYPE_FAVORITE = 1
    const val TYPE_CUSTOM = 2
    const val ID_ALL = "all"
    const val ID_FAV = "fav"

    private const val PREF = "tengmusic_categories"
    private const val KEY = "categories"

    private fun defaults(): List<Category> = listOf(
        Category(ID_ALL, "全部歌曲", TYPE_ALL, 0),
        Category(ID_FAV, "收藏", TYPE_FAVORITE, 1),
    )

    fun load(context: Context): List<Category> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        if (raw.isNullOrEmpty()) return defaults()
        return try {
            val arr = JSONArray(raw)
            val list = (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val type = o.getInt("t")
                val ids = if (o.has("ids")) {
                    val ja = o.getJSONArray("ids")
                    (0 until ja.length()).map { ja.getLong(it) }
                } else emptyList()
                Category(
                    id = o.getString("id"),
                    name = o.getString("n"),
                    type = type,
                    order = o.optInt("o", i),
                    trackIds = ids,
                )
            }
            ensureBuiltIn(list).sortedBy { it.order }
        } catch (_: Throwable) {
            defaults()
        }
    }

    /** 内置两类丢失时补回（排在最前） */
    private fun ensureBuiltIn(list: List<Category>): List<Category> {
        val out = list.toMutableList()
        if (out.none { it.id == ID_ALL }) out.add(0, Category(ID_ALL, "全部歌曲", TYPE_ALL, -2))
        if (out.none { it.id == ID_FAV }) out.add(1, Category(ID_FAV, "收藏", TYPE_FAVORITE, -1))
        return out
    }

    private fun save(context: Context, list: List<Category>) {
        val arr = JSONArray()
        list.forEach { c ->
            val o = JSONObject().apply {
                put("id", c.id)
                put("n", c.name)
                put("t", c.type)
                put("o", c.order)
                if (c.type == TYPE_CUSTOM) {
                    val ja = JSONArray()
                    c.trackIds.forEach { ja.put(it) }
                    put("ids", ja)
                }
            }
            arr.put(o)
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    /** 新增自定义类别（使用系统键盘命名后调用），返回新列表 */
    fun addCategory(context: Context, name: String): List<Category> {
        val list = load(context).toMutableList()
        val maxOrder = list.maxOfOrNull { it.order } ?: 0
        val id = "cat_${System.currentTimeMillis()}"
        list.add(Category(id, name.ifBlank { "新类别" }, TYPE_CUSTOM, maxOrder + 1))
        save(context, list)
        return load(context)
    }

    /** 按给定 id 顺序重排（拖拽后调用），返回新列表 */
    fun reorder(context: Context, orderedIds: List<String>): List<Category> {
        val map = load(context).associateBy { it.id }
        val list = orderedIds.mapNotNull { map[it] }.toMutableList()
        list.forEachIndexed { i, c -> list[i] = c.copy(order = i) }
        // 补全遗漏项（理论上不会发生）
        load(context).forEach { if (it.id !in orderedIds) list.add(it.copy(order = list.size)) }
        save(context, list)
        return load(context)
    }

    /** 删除自定义类别（内置两类不可删），返回新列表 */
    fun removeCategory(context: Context, id: String): List<Category> {
        if (id == ID_ALL || id == ID_FAV) return load(context)
        val list = load(context).filter { it.id != id }
        save(context, list)
        return load(context)
    }
}
