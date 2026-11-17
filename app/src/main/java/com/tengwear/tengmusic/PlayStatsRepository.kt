package com.tengwear.tengmusic

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 听歌时长统计（v1.47）：trackId → 累计播放毫秒数。
 *
 * 持久化到应用私有目录 play_stats.json（JSON 对象），防止退出后重置；
 * VM 在播放轮询中按真实前进的时间差累计（seek / 暂停不计时），
 * 每 30 秒落盘一次，应用退出时再落盘一次。
 */
object PlayStatsRepository {

    private const val FILE = "play_stats.json"

    /** 读取全部累计时长（毫秒），文件不存在 / 损坏返回空表 */
    fun load(context: Context): MutableMap<Long, Long> {
        val out = mutableMapOf<Long, Long>()
        return try {
            val f = File(context.filesDir, FILE)
            if (!f.exists()) return out
            val obj = JSONObject(f.readText())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k.toLongOrNull() ?: continue] = obj.optLong(k, 0L)
            }
            out
        } catch (_: Exception) {
            out
        }
    }

    /** 覆盖保存全部累计时长 */
    fun save(context: Context, stats: Map<Long, Long>) {
        try {
            val obj = JSONObject()
            stats.forEach { (id, ms) -> if (ms > 0) obj.put(id.toString(), ms) }
            File(context.filesDir, FILE).writeText(obj.toString())
        } catch (_: Exception) {
        }
    }
}
