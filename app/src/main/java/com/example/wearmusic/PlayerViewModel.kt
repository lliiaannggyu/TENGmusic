package com.example.wearmusic

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 播放器状态中枢：
 * - 扫描本地音乐、按专辑分组
 * - 连接媒体会话服务（MediaController）
 * - 向 UI 暴露曲目/播放状态/进度/歌词
 * - 音效（EQ）、播放模式、速度、睡眠定时、收藏
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()

    /** 按专辑分组后的列表（iPod 风格「专辑选曲」数据源） */
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums = _albums.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume = _volume.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val lyrics = _lyrics.asStateFlow()

    private val _lyricIndex = MutableStateFlow(-1)
    val lyricIndex = _lyricIndex.asStateFlow()

    // ---------------- 音效 EQ ----------------

    private val _eqEnabled = MutableStateFlow(true)
    val eqEnabled = _eqEnabled.asStateFlow()

    /** 各频段中心频率（Hz）；为空表示均衡器尚未就绪 */
    private val _eqFreqs = MutableStateFlow<List<Int>>(emptyList())
    val eqFreqs = _eqFreqs.asStateFlow()

    /** 各频段当前增益（millibel），与 eqFreqs 一一对应 */
    private val _eqGains = MutableStateFlow<List<Int>>(emptyList())
    val eqGains = _eqGains.asStateFlow()

    private val _eqPreset = MutableStateFlow(EqPresets.ALL.first().name)
    val eqPreset = _eqPreset.asStateFlow()

    // ---------------- 播放模式 / 速度 / 定时 ----------------

    /** Player.REPEAT_MODE_OFF / ONE / ALL */
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle = _shuffle.asStateFlow()

    /** 播放器左下角的三态播放模式（单曲循环 / 列表随机 / 列表顺序） */
    private val _playMode = MutableStateFlow(PlayMode.SEQUENTIAL)
    val playMode = _playMode.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed = _speed.asStateFlow()

    /** 睡眠定时剩余分钟数，0 表示未开启 */
    private val _sleepMinutes = MutableStateFlow(0)
    val sleepMinutes = _sleepMinutes.asStateFlow()

    /** 收藏的曲目 id 集合 */
    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites = _favorites.asStateFlow()

    private var controller: MediaController? = null
    private var sleepJob: Job? = null

    init {
        // 预置默认 5 段均衡器（低频→高频），保证「音效」页未播放时也能看到并手动调节，
        // 而不是只剩一个开/关开关。真实频段会在首次播放 attach 后用设备值覆盖。
        _eqFreqs.value = listOf(60, 230, 910, 3600, 14000)
        _eqGains.value = List(5) { 0 }
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = AudioRepository.loadTracks(getApplication())
            _tracks.value = loaded
            _albums.value = groupTracksIntoAlbums(loaded)
            _favorites.value = FavoritesRepository.load(getApplication())
            connectService()
        }
        viewModelScope.launch { poll() }
    }

    private fun connectService() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, PlaybackService::class.java))
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            controller = future.get()
            val items = _tracks.value.map { MediaItem.fromUri(it.uri) }
            controller?.setMediaItems(items)
            controller?.prepare()
        }, MoreExecutors.directExecutor())
    }

    /** 轮询播放进度与歌词高亮（框架用，足够流畅） */
    private suspend fun poll() {
        while (true) {
            delay(200)
            val c = controller ?: continue
            _position.value = c.currentPosition.coerceAtLeast(0L)
            _duration.value = if (c.duration > 0) c.duration else 0L
            _isPlaying.value = c.isPlaying
            _volume.value = c.volume.coerceIn(0f, 1f)
            _repeatMode.value = c.repeatMode
            _shuffle.value = c.shuffleModeEnabled
            // 反推三态模式：这样「设置页」单独改 repeat/shuffle 时，播放器按钮也能同步
            _playMode.value = when {
                c.repeatMode == Player.REPEAT_MODE_ONE -> PlayMode.REPEAT_ONE
                c.shuffleModeEnabled -> PlayMode.SHUFFLE
                else -> PlayMode.SEQUENTIAL
            }
            _speed.value = c.playbackParameters.speed
            val idx = c.currentMediaItemIndex
            if (idx != _currentIndex.value) {
                _currentIndex.value = idx
                loadLyricsFor(idx)
            }
            syncEq()
            updateLyric(_position.value)
        }
    }

    /** 均衡器就绪后同步频段与默认增益（保留用户已调的增益，避免每次轮询都被重置） */
    private fun syncEq() {
        val count = AudioEffects.bandCount
        if (count > 0) {
            val prevGains = _eqGains.value
            if (_eqFreqs.value.size != count) {
                _eqFreqs.value = AudioEffects.centerFreqsHz
                // 频段数一致则沿用用户增益；否则退回预设默认
                val gains = if (prevGains.size == count) {
                    prevGains
                } else {
                    List(count) { i -> EqPresets.ALL.first().gains.getOrElse(i) { 0 } }
                }
                _eqGains.value = gains
                gains.forEachIndexed { i, g -> AudioEffects.setBandLevel(i, g) }
            }
            AudioEffects.setEnabled(_eqEnabled.value)
        }
    }

    private fun loadLyricsFor(idx: Int) {
        val t = _tracks.value.getOrNull(idx) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _lyrics.value = LyricsRepository.loadLyrics(getApplication(), t)
            _lyricIndex.value = -1
        }
    }

    private fun updateLyric(posMs: Long) {
        val list = _lyrics.value
        if (list.isEmpty()) {
            if (_lyricIndex.value != -1) _lyricIndex.value = -1
            return
        }
        var i = -1
        for (k in list.indices) {
            if (list[k].timeMs <= posMs) i = k else break
        }
        if (i != _lyricIndex.value) _lyricIndex.value = i
    }

    // ---------------- 对外控制 API ----------------

    fun selectTrack(i: Int) {
        controller?.let {
            it.seekToDefaultPosition(i)
            it.play()
        }
    }

    fun togglePlay() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() = controller?.seekToNextMediaItem()

    fun prev() {
        controller?.let {
            if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem()
        }
    }

    fun seekToFraction(f: Float) {
        val d = _duration.value
        if (d > 0) controller?.seekTo((d * f).toLong())
    }

    /**
     * 跳到指定歌词时间戳并继续播放（点击歌词行从该句续播）。
     * 若当前处于暂停状态则自动恢复播放。
     */
    fun seekToLyric(timeMs: Long) {
        controller?.let {
            it.seekTo(timeMs.coerceAtLeast(0L))
            if (!it.isPlaying) it.play()
        }
    }

    /**
     * 相对当前位置 seek（前进/后退），供长按上一首/下一首调进度用。
     * 自动钳制到 [0, duration]，避免溢出。
     */
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val d = if (c.duration > 0) c.duration else _duration.value
        val target = (c.currentPosition + deltaMs).coerceIn(0L, d.coerceAtLeast(0L))
        c.seekTo(target)
    }

    fun setVolume(v: Float) {
        controller?.volume = v.coerceIn(0f, 1f)
        _volume.value = v.coerceIn(0f, 1f)
    }

    /** 播放某张专辑：默认从第一首开始，可指定专辑内第几首 */
    fun playAlbum(album: Album, indexInAlbum: Int = 0) {
        val globalIndex = album.trackIndices.getOrNull(indexInAlbum) ?: return
        selectTrack(globalIndex)
    }

    // ---------------- EQ ----------------

    fun setEqEnabled(on: Boolean) {
        _eqEnabled.value = on
        AudioEffects.setEnabled(on)
    }

    /** 调整单个频段增益（millibel），增益范围见 AudioEffects */
    fun setBandGain(band: Int, millibel: Int) {
        val list = _eqGains.value.toMutableList()
        if (band !in list.indices) return
        val range = AudioEffects.minLevel..AudioEffects.maxLevel
        val v = millibel.coerceIn(range)
        list[band] = v
        _eqGains.value = list
        AudioEffects.setBandLevel(band, v)
        _eqPreset.value = "自定义"
    }

    /** 应用预设；设备频段数与预设长度不一致时自动截断/补零 */
    fun applyPreset(preset: EqPreset) {
        val count = _eqFreqs.value.size
        if (count == 0) return
        val gains = List(count) { i -> preset.gains.getOrElse(i) { 0 } }
        _eqGains.value = gains
        _eqPreset.value = preset.name
        gains.forEachIndexed { i, g -> AudioEffects.setBandLevel(i, g) }
    }

    // ---------------- 播放模式 / 速度 / 定时 ----------------

    /** 顺序 → 列表循环 → 单曲循环 → 顺序 */
    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = next
        controller?.repeatMode = next
    }

    fun toggleShuffle() {
        val next = !_shuffle.value
        _shuffle.value = next
        controller?.shuffleModeEnabled = next
    }

    /** 设置三态播放模式（同步到底层 repeatMode + shuffle） */
    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
        val (repeat, shuffleOn) = when (mode) {
            PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE to false
            PlayMode.SHUFFLE -> Player.REPEAT_MODE_ALL to true
            PlayMode.SEQUENTIAL -> Player.REPEAT_MODE_ALL to false
        }
        _repeatMode.value = repeat
        _shuffle.value = shuffleOn
        controller?.let {
            it.repeatMode = repeat
            it.shuffleModeEnabled = shuffleOn
        }
    }

    /** 单曲循环 → 列表随机 → 列表顺序 → 单曲循环 */
    fun cyclePlayMode() = setPlayMode(_playMode.value.next())

    fun setSpeed(v: Float) {
        val s = v.coerceIn(0.5f, 2.0f)
        _speed.value = s
        controller?.setPlaybackSpeed(s)
    }

    /** 睡眠定时：minutes<=0 取消；到点自动暂停 */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        _sleepMinutes.value = minutes
        if (minutes <= 0) return
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _sleepMinutes.value = 0
        }
    }

    /** 切换收藏 */
    fun toggleFavorite(trackId: Long) {
        _favorites.value = FavoritesRepository.toggle(getApplication(), trackId)
    }
}
