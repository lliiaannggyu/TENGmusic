package com.example.wearmusic

/**
 * LRC 歌词解析。
 * 支持 [mm:ss.xx] 与 [mm:ss.xxx] 两种写法，一行可带多个时间标签。
 */
object LrcParser {

    private val TAG = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")

    fun parse(text: String): List<LrcLine> {
        val out = mutableListOf<LrcLine>()
        for (line in text.lineSequence()) {
            val times = mutableListOf<Long>()
            for (m in TAG.findAll(line)) {
                val mm = m.groupValues[1].toLongOrNull() ?: 0L
                val ss = m.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = m.groupValues[3]
                val fracMs = if (fracStr.isNotEmpty()) {
                    (fracStr.padEnd(3, '0').toLongOrNull() ?: 0L)
                } else 0L
                times.add((mm * 60 + ss) * 1000 + fracMs)
            }
            val content = line.replace(TAG, "").trim().ifBlank { "♪" }
            times.forEach { out.add(LrcLine(it, content)) }
        }
        out.sortBy { it.timeMs }
        return out
    }
}
