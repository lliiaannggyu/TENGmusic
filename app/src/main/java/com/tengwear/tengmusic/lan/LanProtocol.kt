package com.tengwear.tengmusic.lan

import org.json.JSONObject

/**
 * 局域网传歌 / 一起听 的共享常量与线上协议。
 *
 * 传输模型（全部为点对点，不经过任何服务器）：
 *  - 发现：UDP 广播（端口 [UDP_PORT]），每 [BROADCAST_PERIOD_MS] 喊一次 hello，
 *    超过 [PEER_TIMEOUT_MS] 没再见到的设备移出列表；退出时发 bye 立即消失。
 *  - 传输：TCP（端口 [TCP_PORT]），先发一行 JSON 头（\\n 结尾），再跟原始字节流。
 *    接收方读头 → 弹给用户决定接受/拒绝 → 接受则回 "OK" 并读取 size 字节。
 *
 * 头字段（kind= song 为传歌，listen 为一起听推送）：
 *  {"kind":"song|listen","id":"<传输id>","name":"xxx.mp3","size":123,
 *   "compressed":false,"title":"歌名","artist":"艺术家","lyric":"[00:12]…"}
 */
object LanProtocol {
    /** 发现：UDP 广播端口 */
    const val UDP_PORT = 47771

    /** 传输：TCP 监听端口 */
    const val TCP_PORT = 47772

    /** 广播周期（毫秒） */
    const val BROADCAST_PERIOD_MS = 2000L

    /** peer 超时（毫秒）：超过未再见即视为离线 */
    const val PEER_TIMEOUT_MS = 5000L

    /** 传输类型：传歌（接收方落媒体库，成为本地曲目） */
    const val KIND_SONG = "song"

    /** 传输类型：一起听（接收方落缓存目录，退出即删，不留残余） */
    const val KIND_LISTEN = "listen"

    const val REPLY_OK = "OK"
    const val REPLY_NO = "NO"

    /** 一起听控制命令类型（会话连接上后续行的 JSON 字段 t） */
    const val CTRL_STATE = "state"   // 周期 tick / 事件：{idx,pos,playing}（嘉宾据此对齐）
    const val CTRL_STOP = "stop"     // 主播结束会话：嘉宾退出一起听
}

/** 一起听歌单里的一首（主播推送、嘉宾缓存时一并携带元信息） */
data class SharedMeta(
    val title: String,
    val artist: String,
    val lyric: String?,
    val durMs: Long,
)

/** 一个被发现的对端设备 */
data class LanPeer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = LanProtocol.TCP_PORT,
    val lastSeen: Long = System.currentTimeMillis(),
)

/** 一条传输的实时状态（UI 直接渲染） */
data class TransferState(
    val id: String,
    val peerName: String,
    val fileName: String,
    val kind: String,
    val sending: Boolean,      // true = 我在发；false = 我在收
    val done: Long = 0L,
    val total: Long = 0L,      // 0 = 大小未知（不确定进度）
    val finished: Boolean = false,
    val ok: Boolean = false,
    val message: String = "",
) {
    val fraction: Float?
        get() = if (total > 0L) (done.toFloat() / total).coerceIn(0f, 1f) else null
}

/** 网络层向 UI 抛出的事件（一次性） */
sealed interface LanEvent {
    /** 对端想传一首歌给你（待决定）——同时以 [com.tengwear.tengmusic.lan.LanManager.pendingSongOffer] 状态形式提供，防丢 */
    data class SongOffer(
        val transferId: String,
        val peer: LanPeer,
        val name: String,
        val size: Long,
        val compressed: Boolean,
    ) : LanEvent

    /** 对端想和你一起听（一次性邀请：含整张临时歌单，待决定） */
    data class ListenOffer(
        val transferId: String,
        val peer: LanPeer,
        val count: Int,
        val queue: List<SharedMeta>,
    ) : LanEvent

    /** 传输进度 */
    data class Progress(val transferId: String, val done: Long, val total: Long) : LanEvent

    /**
     * 传输结束。
     * @param kind   "song" / "listen"（UI 据此走不同后续：落库刷新 / 起播）
     * @param sending true = 发送方视角；false = 接收方视角
     * @param lyric  一起听推送的歌词原文（接收成功时透传，直接起播用）
     */
    data class Done(
        val transferId: String,
        val ok: Boolean,
        val message: String,
        val kind: String = "",
        val sending: Boolean = true,
        val title: String = "",
        val artist: String = "",
        val lyric: String? = null,
    ) : LanEvent
}

/** 发现协议的 JSON 助手 */
internal object LanJson {
    fun hello(id: String, name: String): String =
        JSONObject().apply {
            put("t", "hello")
            put("id", id)
            put("name", name)
        }.toString()

    fun bye(id: String): String =
        JSONObject().apply {
            put("t", "bye")
            put("id", id)
        }.toString()

    fun parse(text: String): JSONObject? = runCatching { JSONObject(text) }.getOrNull()
}
