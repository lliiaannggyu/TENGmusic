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
        player = ExoPlayer.Builder(this).build().apply {
            // 音频会话就绪后挂载均衡器：EQ 只有拿到 audioSessionId 才能对播放链路生效
            addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    AudioEffects.attach(audioSessionId)
                }
            })
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
