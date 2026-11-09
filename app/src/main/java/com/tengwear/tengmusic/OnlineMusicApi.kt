package com.tengwear.tengmusic

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 一条在线搜索结果 */
data class OnlineSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
) {
    val durationText: String
        get() {
            val s = durationMs / 1000
            return "%d:%02d".format(s / 60, s % 60)
        }
}

/**
 * 在线音源访问层（网易云接口兼容协议，HttpURLConnection 零依赖实现）。
 *
 * 搜索：  GET {base}/search?keywords=&type=1&limit=&page=
 * 取链接：GET {base}/song/url/v1?id=&level=standard，失败回退 {base}/song/url?id=
 * 响应解析宽松：兼容 result.songs / data(数组或对象) / songs / list 等常见结构。
 */
object OnlineMusicApi {

    private const val UA = "Mozilla/5.0 (Linux; Android 13; Wear OS) TENGMUSIC/1.35"

    /** 搜索歌曲；网络失败/解析失败抛 IOException */
    fun search(baseUrl: String, keyword: String, page: Int = 1, limit: Int = 30): List<OnlineSong> {
        val base = trimBase(baseUrl)
        val url = base + "/search?keywords=" + enc(keyword) +
            "&type=1&limit=" + limit + "&page=" + page +
            "&offset=" + (page - 1) * limit
        val text = httpText(url) ?: throw IOException("搜索请求失败（网络/音源不可达）")
        val songs = parseSongs(text)
        if (songs.isEmpty()) throw IOException("音源返回了无法解析的结果")
        return songs
    }

    /** 取歌曲播放直链；全部候选接口失败抛 IOException */
    fun playUrl(baseUrl: String, id: String): String {
        val base = trimBase(baseUrl)
        val candidates = listOf(
            base + "/song/url/v1?id=" + enc(id) + "&level=standard",
            base + "/song/url?id=" + enc(id),
            base + "/song/url/v1?id=" + enc(id),
        )
        for (u in candidates) {
            val text = httpText(u) ?: continue
            val link = parsePlayUrl(text)
            if (!link.isNullOrBlank() && link.startsWith("http")) return link
        }
        throw IOException("取播放链接失败（该音源可能不提供此歌曲）")
    }

    /**
     * 下载音频到输出流；[onProgress] 回调已下载字节。
     * 音源直链可能是 302 重定向，HttpURLConnection 默认会跟随同协议跳转。
     */
    fun download(audioUrl: String, out: OutputStream, onProgress: (Long) -> Unit) {
        val conn = URL(audioUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            if (conn.responseCode !in 200..299) throw IOException("下载 HTTP " + conn.responseCode)
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                var got = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    got += n
                    onProgress(got)
                }
                out.flush()
            }
        } finally {
            conn.disconnect()
        }
    }

    // ---------------- 内部：HTTP 与 JSON 宽松解析 ----------------

    private fun httpText(url: String, timeoutMs: Int = 12000): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else null
        } finally {
            conn.disconnect()
        }
    } catch (_: Throwable) {
        null
    }

    /** 从文本里解析歌曲数组；找不到任何可识别结构时抛 IOException */
    internal fun parseSongs(text: String): List<OnlineSong> {
        val root = try {
            JSONObject(text)
        } catch (_: Throwable) {
            // 有些音源直接返回数组
            try {
                return JSONArray(text).let { collectSongs(it) }
            } catch (_: Throwable) {
                throw IOException("音源返回格式无法解析")
            }
        }
        val arr = root.optJSONObject("result")?.optJSONArray("songs")
            ?: root.optJSONObject("result")?.optJSONArray("list")
            ?: root.optJSONObject("data")?.optJSONArray("songs")
            ?: root.optJSONObject("data")?.optJSONArray("list")
            ?: root.optJSONArray("data")
            ?: root.optJSONArray("songs")
            ?: root.optJSONArray("list")
            ?: root.optJSONArray("result")
        return if (arr != null) collectSongs(arr) else emptyList()
    }

    private fun collectSongs(arr: JSONArray): List<OnlineSong> {
        val list = mutableListOf<OnlineSong>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "").ifBlank {
                o.optString("songId", "").ifBlank {
                    val l = o.optLong("id", -1L)
                    if (l > 0) l.toString() else ""
                }
            }
            if (id.isBlank()) continue
            val title = o.optString("name", "").ifBlank { o.optString("title", "") }
            if (title.isBlank()) continue
            list.add(
                OnlineSong(
                    id = id,
                    title = title,
                    artist = artistOf(o),
                    album = albumOf(o),
                    durationMs = o.optLong("duration", 0L).takeIf { it > 0 }
                        ?: o.optLong("durationMs", 0L).takeIf { it > 0 }
                        ?: o.optLong("dt", 0L),
                )
            )
        }
        return list
    }

    private fun artistOf(o: JSONObject): String {
        // artists: [{name},...]（网易云）
        val arr = o.optJSONArray("artists")
        if (arr != null && arr.length() > 0) {
            val names = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
                .filter { it.isNotBlank() }
            if (names.isNotEmpty()) return names.joinToString("/")
        }
        return o.optString("artist", "")
            .ifBlank { o.optString("artistName", "") }
            .ifBlank { o.optString("singer", "") }
    }

    private fun albumOf(o: JSONObject): String {
        val albumObj = o.optJSONObject("album")
        if (albumObj != null) {
            val n = albumObj.optString("name", "")
            if (n.isNotBlank()) return n
        }
        return o.optString("album", "").ifBlank { o.optString("albumName", "") }
    }

    private fun parsePlayUrl(text: String): String? = try {
        val root = JSONObject(text)
        val data = root.optJSONArray("data")?.optJSONObject(0)
            ?: root.optJSONObject("data")
        data?.optString("url", "")?.ifBlank { null }
            ?: root.optString("url", "").ifBlank { null }
    } catch (_: Throwable) {
        null
    }

    private fun trimBase(b: String): String = b.trim().trimEnd('/')

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
