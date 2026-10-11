package com.example.wearmusic

/** 均衡器预设：gains 单位为毫贝尔(millibel)，按低频→高频顺序对应各频段 */
data class EqPreset(val name: String, val gains: IntArray)

/**
 * 常用 EQ 预设。
 * 预设按 5 段定义；若设备实际频段数不同，PlayerViewModel 会自动截断/补零以适配。
 */
object EqPresets {
    val ALL: List<EqPreset> = listOf(
        EqPreset("正常", intArrayOf(0, 0, 0, 0, 0)),
        EqPreset("摇滚", intArrayOf(500, 300, -200, 200, 300)),
        EqPreset("流行", intArrayOf(-100, 200, 500, 200, -100)),
        EqPreset("爵士", intArrayOf(400, 200, -200, 200, 400)),
        EqPreset("古典", intArrayOf(300, 100, -100, 100, 300)),
        EqPreset("电子", intArrayOf(600, 400, 0, 300, 500)),
        EqPreset("人声", intArrayOf(-300, 0, 300, 400, 200)),
        EqPreset("重低音", intArrayOf(700, 500, 0, 0, 0))
    )
}
