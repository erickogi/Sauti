package io.sauti.android.ring

import android.content.Context
import android.media.AudioManager

internal class AudioManagerRingerMode(
    context: Context
) : RingerModeProvider {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun current(): RingerMode = when (audioManager.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
        AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
        else -> RingerMode.NORMAL
    }
}
