package com.tengwear.tengmusic

/**
 * 自定义在线音源：一个 API 基址 + 展示名。
 *
 * 协议约定（网易云接口兼容，国内公共音源大多适用）：
 * - 搜索   GET {base}/search?keywords=关键词&type=1&limit=30&page=1
 * - 取链接 GET {base}/song/url/v1?id=<id>&level=standard   （回退 {base}/song/url?id=<id>）
 * 响应按常见结构宽松解析（result.songs / data / songs / list 均可）。
 */
data class MusicSource(
    /** 展示名（可留空，默认用域名） */
    val name: String,
    /** API 基址，如 https://example.com */
    val baseUrl: String,
) {
    val displayName: String
        get() = name.ifBlank {
            baseUrl.removePrefix("https://").removePrefix("http://").substringBefore('/')
        }
}
