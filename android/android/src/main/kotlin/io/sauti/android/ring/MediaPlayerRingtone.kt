package io.sauti.android.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

internal class MediaPlayerRingtone(
    private val context: Context,
    private val overrideUri: Uri?
) : RingtonePlayer {

    private var player: MediaPlayer? = null

    override fun start() {
        if (player != null) return
        val uri = overrideUri
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        val created = MediaPlayer()
        try {
            created.setDataSource(context, uri)
            created.isLooping = true
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            created.prepare()
            created.start()
            player = created
        } catch (error: Throwable) {
            created.release()
        }
    }

    override fun stop() {
        val current = player ?: return
        runCatching {
            if (current.isPlaying) current.stop()
        }
        current.release()
        player = null
    }

    override fun release() {
        stop()
    }
}
