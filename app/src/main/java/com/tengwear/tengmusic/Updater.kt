package com.tengwear.tengmusic

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新检查（v1.44）。
 *
 * 通过 GitHub Releases API 拉取最新发布版本，与当前安装版本比较；
 * 发现新版本时返回 APK 直链，由设置页触发系统 DownloadManager 下载，
 * 下载完成后在通知栏点开即可安装（与旧版本同包名，直接覆盖升级）。
 */
object Updater {

    data class UpdateInfo(
        val latestVersion: String,   // 不含 v 前缀，如 "1.44"
        val downloadUrl: String,     // APK 直链
        val releaseNotes: String,    // Release 说明（截断）
    )

    private const val API =
        "https://api.github.com/repos/lliiaannggyu/TENGmusic/releases/latest"

    fun currentVersion(): String = BuildConfig.VERSION_NAME

    /** 版本号比较：latest > current 时返回 true（支持多段数字，如 1.9 < 1.10） */
    fun isNewer(latest: String, current: String): Boolean {
        fun parse(v: String) = v.trim().removePrefix("v")
            .split(".").map { it.toIntOrNull() ?: 0 }
        val a = parse(latest)
        val b = parse(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * 拉取最新 Release 信息。网络失败 / 非 200 / 解析失败均返回 null。
     * 阻塞网络调用，必须在 IO 线程调用。
     */
    fun fetchLatest(): UpdateInfo? {
        return try {
            val conn = URL(API).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "TENGMUSIC-App")
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(text)
            val tag = obj.optString("tag_name", "").trim()
            if (tag.isEmpty()) return null

            // 找 .apk 资产直链；找不到就退回 Release 页
            var url = ""
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name", "").endsWith(".apk")) {
                        url = a.optString("browser_download_url", "")
                        break
                    }
                }
            }
            UpdateInfo(
                latestVersion = tag.removePrefix("v"),
                downloadUrl = url.ifEmpty {
                    "https://github.com/lliiaannggyu/TENGmusic/releases/latest"
                },
                releaseNotes = obj.optString("body", "").take(300),
            )
        } catch (_: Exception) {
            null
        }
    }
}
