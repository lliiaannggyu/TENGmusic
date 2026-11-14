package com.tengwear.tengmusic

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 媒体播放后台服务。
 * 用 Media3 的 MediaSessionService + ExoPlayer，
 * 自动提供前台通知、系统/手表媒体控件、蓝牙/线控。
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            // 耳机断开（拔线/蓝牙断连触发 AUDIO_BECOMING_NOISY）时自动暂停播放
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
            // 显式设置音频属性：没有它 ExoPlayer 可能到首次播放才初始化音频 sink，
            // audioSessionId 迟迟不生成 → 均衡器挂不上（EQ 调了没效果的根因之一）
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            // 音频会话就绪后挂载均衡器：EQ 只有拿到 audioSessionId 才能对播放链路生效
            addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    AudioEffects.attach(audioSessionId)
                }
            })
            // 兜底：若 session id 已在 addListener 之前生成，直接挂载
            if (audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                AudioEffects.attach(audioSessionId)
            }
        }
        session = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionPendingIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        AudioEffects.release()
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }

    private fun sessionPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
