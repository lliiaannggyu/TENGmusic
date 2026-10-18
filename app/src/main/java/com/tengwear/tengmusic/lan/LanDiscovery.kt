package com.tengwear.tengmusic.lan

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * UDP 广播设备发现：
 *  - 每 2s 向 255.255.255.255:47771 广播 hello（含本机 id 与设备名）
 *  - 收到对端 hello 时记录/刷新该 peer，并**单播回一个 hello**（让对端也立刻知道我们，
 *    不必等它的下一轮广播，发现更快）
 *  - 收到 bye 立即移除；超过 5s 未再见到的 peer 由 pruneLoop 清除
 *
 * 线程：socket 收发与维护协程全部在 Dispatchers.IO。
 */
class LanDiscovery(
    private val deviceId: String,
    private val deviceName: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val peers = ConcurrentHashMap<String, LanPeer>()

    private val _peerList = MutableStateFlow<List<LanPeer>>(emptyList())
    val peerList: StateFlow<List<LanPeer>> = _peerList.asStateFlow()

    private var socket: DatagramSocket? = null

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        scope.launch { receiveLoop() }
        scope.launch { broadcastLoop() }
        scope.launch { pruneLoop() }
    }

    fun stop() {
        running = false
        // 告知对端我们下线，省得对方白等 5s 超时
        runCatching {
            socket?.let { s ->
                val data = LanJson.bye(deviceId).toByteArray()
                s.send(DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), LanProtocol.UDP_PORT))
            }
        }
        runCatching { socket?.close() }
        socket = null
        peers.clear()
        _peerList.value = emptyList()
    }

    private suspend fun broadcastLoop() {
        while (running) {
            runCatching {
                val s = ensureSocket()
                val data = LanJson.hello(deviceId, deviceName).toByteArray()
                s.send(
                    DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), LanProtocol.UDP_PORT)
                )
            }
            delay(LanProtocol.BROADCAST_PERIOD_MS)
        }
    }

    private suspend fun receiveLoop() {
        val buf = ByteArray(2048)
        while (running) {
            try {
                val s = ensureSocket()
                val packet = DatagramPacket(buf, buf.size)
                s.receive(packet) // 阻塞；stop() 时 close 会抛 SocketException 退出
                val text = String(packet.data, 0, packet.length)
                val json = LanJson.parse(text) ?: continue
                when (json.optString("t")) {
                    "hello" -> {
                        val id = json.optString("id")
                        if (id.isNotEmpty() && id != deviceId) {
                            val name = json.optString("name").ifEmpty { "未知设备" }
                            peers[id] = LanPeer(
                                id = id,
                                name = name,
                                host = packet.address.hostAddress ?: "",
                                lastSeen = System.currentTimeMillis(),
                            )
                            publish()
                            // 单播回hello：让对方下一拍前就知道我们
                            val reply = LanJson.hello(deviceId, deviceName).toByteArray()
                            runCatching {
                                s.send(
                                    DatagramPacket(reply, reply.size, packet.address, LanProtocol.UDP_PORT)
                                )
                            }
                        }
                    }
                    "bye" -> {
                        val id = json.optString("id")
                        if (peers.remove(id) != null) publish()
                    }
                }
            } catch (e: Throwable) {
                if (!running) return
                Log.w(TAG, "receiveLoop error", e)
                delay(500)
            }
        }
    }

    private suspend fun pruneLoop() {
        while (running) {
            delay(1000)
            val now = System.currentTimeMillis()
            val dead = peers.values.filter { now - it.lastSeen > LanProtocol.PEER_TIMEOUT_MS }
            if (dead.isNotEmpty()) {
                dead.forEach { peers.remove(it.id) }
                publish()
            }
        }
    }

    private fun publish() {
        _peerList.value = peers.values.sortedBy { it.name }
    }

    private fun ensureSocket(): DatagramSocket {
        socket?.let { if (!it.isClosed) return it }
        return DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(java.net.InetSocketAddress(LanProtocol.UDP_PORT))
            socket = this
        }
    }

    private companion object {
        const val TAG = "LanDiscovery"
    }
}
