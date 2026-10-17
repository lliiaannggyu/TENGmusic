package com.tengwear.tengmusic

/**
 * 播放模式（播放器左下角按钮三态循环）。
 *
 * - [REPEAT_ONE] 单曲循环：repeatMode = REPEAT_MODE_ONE，shuffle 关闭
 * - [SHUFFLE]    列表随机：repeatMode = REPEAT_MODE_ALL，shuffle 开启
 * - [SEQUENTIAL] 列表顺序：repeatMode = REPEAT_MODE_ALL，shuffle 关闭
 *
 * 三态互斥，避免「随机 + 单曲循环」这类语义冲突的组合。
 */
enum class PlayMode(val label: String) {
    REPEAT_ONE("单曲循环"),
    SHUFFLE("列表随机"),
    SEQUENTIAL("列表顺序"),
}

/** 单曲循环 → 列表随机 → 列表顺序 → 单曲循环 */
fun PlayMode.next(): PlayMode = when (this) {
    PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
    PlayMode.SHUFFLE -> PlayMode.SEQUENTIAL
    PlayMode.SEQUENTIAL -> PlayMode.REPEAT_ONE
}
