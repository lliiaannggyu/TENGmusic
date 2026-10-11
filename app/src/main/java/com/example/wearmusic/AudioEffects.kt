package com.example.wearmusic

import android.media.audiofx.Equalizer

/**
 * 均衡器（EQ）封装：基于系统 android.media.audiofx.Equalizer，
 * 通过 ExoPlayer 的 audioSessionId 挂载到播放链路上实时生效。
 *
 * - PlaybackService 在 onAudioSessionIdChanged 回调里 attach(sessionId)
 * - PlayerViewModel 直接读写增益（与 Service 同进程，无需跨进程命令）
 * - 挂载失败（部分设备/模拟器不支持）时 bandCount = 0，UI 需降级提示
 */
object AudioEffects {

    private var equalizer: Equalizer? = null

    /** 可用频段数量；未成功挂载时为 0 */
    @Volatile
    var bandCount: Int = 0
        private set

    /** 增益下限（毫贝尔 millibel），常见 -1500 */
    @Volatile
    var minLevel: Int = -1500
        private set

    /** 增益上限（毫贝尔 millibel），常见 1500 */
    @Volatile
    var maxLevel: Int = 1500
        private set

    /** 各频段中心频率（Hz），顺序与 band 下标一致 */
    private val freqs = mutableListOf<Int>()
    val centerFreqsHz: List<Int> get() = freqs

    @Volatile
    var enabled: Boolean = false
        private set

    /** 挂载均衡器到指定音频会话 */
    fun attach(sessionId: Int) {
        release()
        if (sessionId <= 0) return
        try {
            val eq = Equalizer(0, sessionId)
            val n = eq.numberOfBands.toInt().coerceIn(1, 12)
            val range = eq.bandLevelRange              // ShortArray[min, max]，单位 millibel
            val centers = ArrayList<Int>(n)
            for (b in 0 until n) {
                centers.add(eq.getCenterFreq(b.toShort()) / 1000)   // milliHz -> Hz
            }
            freqs.clear()
            freqs.addAll(centers)
            bandCount = n
            minLevel = range[0].toInt()
            maxLevel = range[1].toInt()
            eq.enabled = true
            equalizer = eq
            enabled = true
        } catch (t: Throwable) {
            equalizer = null
            bandCount = 0
            enabled = false
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        try {
            equalizer?.enabled = on
        } catch (t: Throwable) {
            // 部分设备不支持动态开关，忽略
        }
    }

    /** 设置某频段增益（millibel，自动夹取到设备允许范围） */
    fun setBandLevel(band: Int, millibel: Int) {
        val eq = equalizer ?: return
        try {
            eq.setBandLevel(band.toShort(), millibel.coerceIn(minLevel, maxLevel).toShort())
        } catch (t: Throwable) {
            // 非法频段下标，忽略
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (t: Throwable) {
            // ignore
        }
        equalizer = null
        bandCount = 0
        enabled = false
        freqs.clear()
    }
}
