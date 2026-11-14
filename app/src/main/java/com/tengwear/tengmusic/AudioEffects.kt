package com.tengwear.tengmusic

import android.media.audiofx.Equalizer

/**
 * 均衡器管理器（频段增益 / 预设 / 中心频率 / 量程的完整管理类形态）。
 *
 * 结构移植自 GitHub 高星开源项目 bullheadandplato/AndroidEqualizer（252★，Apache-2.0，
 * https://github.com/bullheadandplato/AndroidEqualizer ）的 EqualizerManager：
 *  - setup / release 生命周期（按优先级 + 音频会话挂载）
 *  - enable / disable 动态开关
 *  - getBandLevel / setBandLevel 频段增益读写（millibel）
 *  - getCenterFreq / getBandLevelRange 设备能力查询
 *  - getNumberOfPresets / getPresetName / usePreset 设备内置预设切换
 *  - 频段变化/预设切换的回调通知
 *
 * 底层仍是系统 android.media.audiofx.Equalizer，通过 ExoPlayer 的 audioSessionId
 * 挂载到播放链路上实时生效。挂载失败（部分设备/模拟器不支持）时 isReady = false，
 * UI 需降级提示。
 *
 * @param priority 效果器优先级（AndroidEqualizer 默认 1000）
 */
class EqualizerManager(private val priority: Int = DEFAULT_PRIORITY) {

    companion object {
        const val DEFAULT_PRIORITY = 1000
        /** 设备频段数上限保护，防止异常设备返回超大值导致 UI 溢出 */
        const val MAX_BANDS = 12
    }

    private var equalizer: Equalizer? = null

    // ---------------- 挂载前调节暂存（v1.44 修复「EQ 调了没效果」）----------------
    // 时序坑：均衡器要等播放开始（onAudioSessionIdChanged）才挂载，用户若在挂载前
    // 调节增益/开关，此前直接被丢弃 → 滑杆在动、音频链路没变化。现把挂载前的
    // 所有调节暂存，setup 成功后立刻全部重放，保证任何时序下调节都生效。
    private val pendingGains = mutableMapOf<Int, Int>()
    private var pendingEnabled: Boolean? = null

    /** 当前挂载的音频会话 id；0 表示未挂载 */
    @Volatile
    var audioSessionId: Int = 0
        private set

    /** 均衡器是否已成功挂载并就绪 */
    @Volatile
    var isReady: Boolean = false
        private set

    /** 是否处于启用状态 */
    @Volatile
    var isEnabled: Boolean = false
        private set

    /** 可用频段数量；未就绪时为 0 */
    val numberOfBands: Int
        get() = if (isReady) bandCount else 0

    private var bandCount: Int = 0

    /** 增益范围（millibel），如 -1500..1500 */
    val bandLevelRange: IntRange
        get() = minLevel..maxLevel

    private var minLevel: Int = -1500
    private var maxLevel: Int = 1500

    /** 各频段中心频率（Hz），顺序与 band 下标一致 */
    private val centerFreqs = IntArray(MAX_BANDS)

    /** 设备内置预设数量（0 = 设备不提供预设） */
    val numberOfPresets: Int
        get() = try {
            equalizer?.numberOfPresets?.toInt() ?: 0
        } catch (_: Throwable) {
            0
        }

    /** 频段增益变化回调（band, level），供 UI/ViewModel 同步 */
    var onBandLevelChanged: ((band: Int, level: Int) -> Unit)? = null

    /** 预设切换回调（presetIndex） */
    var onPresetChanged: ((preset: Int) -> Unit)? = null

    // ---------------- 生命周期（移植自 AndroidEqualizer EqualizerManager） ----------------

    /**
     * 按优先级挂载均衡器到指定音频会话。
     * 重复调用会先释放旧实例；sessionId <= 0 视为无效直接返回。
     */
    fun setup(sessionId: Int) {
        release()
        if (sessionId <= 0) return
        try {
            val eq = Equalizer(priority, sessionId)
            val n = eq.numberOfBands.toInt().coerceIn(1, MAX_BANDS)
            val range = eq.bandLevelRange // ShortArray[min, max]，单位 millibel
            for (b in 0 until n) {
                centerFreqs[b] = eq.getCenterFreq(b.toShort()) / 1000 // milliHz -> Hz
            }
            bandCount = n
            minLevel = range[0].toInt()
            maxLevel = range[1].toInt()
            eq.enabled = true
            equalizer = eq
            audioSessionId = sessionId
            isReady = true
            isEnabled = true
            // 重放挂载前的暂存调节（增益 → 开关），保证早于挂载的设置生效
            try {
                pendingGains.forEach { (b, v) ->
                    if (b in 0 until n) {
                        eq.setBandLevel(b.toShort(), v.coerceIn(minLevel, maxLevel).toShort())
                    }
                }
                pendingEnabled?.let { eq.enabled = it; isEnabled = it }
            } catch (_: Throwable) {
            }
            pendingGains.clear()
            pendingEnabled = null
        } catch (t: Throwable) {
            equalizer = null
            reset()
        }
    }

    /** 释放均衡器（切歌 / 会话销毁时调用） */
    fun release() {
        try {
            equalizer?.release()
        } catch (_: Throwable) {
            // ignore
        }
        equalizer = null
        reset()
    }

    private fun reset() {
        bandCount = 0
        minLevel = -1500
        maxLevel = 1500
        audioSessionId = 0
        isReady = false
        isEnabled = false
        centerFreqs.fill(0)
    }

    // ---------------- 开关 / 增益 ----------------

    /** 动态启用/禁用均衡器（部分设备不支持动态开关，失败时静默降级） */
    fun setEnabled(on: Boolean) {
        isEnabled = on
        if (!isReady) {
            // 未挂载：暂存，挂载后自动应用
            pendingEnabled = on
            return
        }
        try {
            equalizer?.enabled = on
        } catch (_: Throwable) {
            // 部分设备不支持动态开关，忽略
        }
    }

    /** 读取某频段增益（millibel），未就绪返回 0 */
    fun getBandLevel(band: Int): Int {
        if (!isReady || band !in 0 until bandCount) return 0
        return try {
            equalizer?.getBandLevel(band.toShort())?.toInt() ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    /** 设置某频段增益（millibel，自动夹取到设备允许范围） */
    fun setBandLevel(band: Int, millibel: Int) {
        if (band !in 0 until maxOf(bandCount, MAX_BANDS)) return
        if (!isReady) {
            // 未挂载：暂存，挂载后自动应用（修复「EQ 调了没效果」的时序 bug）
            pendingGains[band] = millibel
            return
        }
        val v = millibel.coerceIn(minLevel, maxLevel)
        try {
            equalizer?.setBandLevel(band.toShort(), v.toShort())
            onBandLevelChanged?.invoke(band, v)
        } catch (_: Throwable) {
            // 非法频段下标，忽略
        }
    }

    /** 读取某频段中心频率（Hz），未就绪返回 0 */
    fun getCenterFreq(band: Int): Int {
        if (!isReady || band !in 0 until bandCount) return 0
        return centerFreqs[band]
    }

    /** 各频段中心频率（Hz）快照，顺序与 band 下标一致 */
    fun centerFreqsHz(): List<Int> = (0 until bandCount).map { centerFreqs[it] }

    // ---------------- 设备内置预设（移植自 AndroidEqualizer EqualizerManager） ----------------

    /** 设备第 index 个内置预设的名称；越界返回 null */
    fun getPresetName(index: Int): String? {
        if (index < 0 || index >= numberOfPresets) return null
        return try {
            equalizer?.getPresetName(index.toShort())
        } catch (_: Throwable) {
            null
        }
    }

    /** 应用设备内置预设（同时把各频段增益写入并回调通知） */
    fun usePreset(index: Int) {
        if (!isReady || index < 0 || index >= numberOfPresets) return
        try {
            equalizer?.usePreset(index.toShort())
            for (b in 0 until bandCount) {
                onBandLevelChanged?.invoke(b, getBandLevel(b))
            }
            onPresetChanged?.invoke(index)
        } catch (_: Throwable) {
            // ignore
        }
    }
}

/**
 * 进程内单例门面：保留原有静态调用方式（PlaybackService.attach / PlayerViewModel 读写增益），
 * 内部委托给 [EqualizerManager] 实例。
 */
object AudioEffects {

    private val manager = EqualizerManager()

    /** 挂载/切换均衡器（PlaybackService.onAudioSessionIdChanged 调用） */
    fun attach(sessionId: Int) = manager.setup(sessionId)

    fun release() = manager.release()

    /** 可用频段数量；未成功挂载时为 0 */
    val bandCount: Int get() = manager.numberOfBands

    /** 增益下限（毫贝尔 millibel），常见 -1500 */
    val minLevel: Int get() = manager.bandLevelRange.first

    /** 增益上限（毫贝尔 millibel），常见 1500 */
    val maxLevel: Int get() = manager.bandLevelRange.last

    /** 各频段中心频率（Hz），顺序与 band 下标一致 */
    val centerFreqsHz: List<Int> get() = manager.centerFreqsHz()

    /** 设备内置预设名称列表（空 = 设备不提供预设） */
    val devicePresetNames: List<String>
        get() = (0 until manager.numberOfPresets).mapNotNull { manager.getPresetName(it) }

    fun setEnabled(on: Boolean) = manager.setEnabled(on)

    /** 设置某频段增益（millibel，自动夹取到设备允许范围） */
    fun setBandLevel(band: Int, millibel: Int) = manager.setBandLevel(band, millibel)

    /** 应用设备内置预设（index 见 [devicePresetNames]） */
    fun useDevicePreset(index: Int) = manager.usePreset(index)

    /** 均衡器管理器实例（需要回调/预设能力时直接用） */
    fun manager(): EqualizerManager = manager
}
