package io.sauti.android.ring

import android.content.Context

class SautiIncomingRing internal constructor(
    private val ringtone: RingtonePlayer,
    private val vibrator: CallVibrator,
    private val ringerMode: RingerModeProvider
) {

    private var started = false

    fun start() {
        if (started) return
        started = true
        when (val mode = RingReducer.incoming(ringerMode.current())) {
            is RingMode.Incoming -> {
                if (mode.sound) ringtone.start()
                vibrator.start()
            }
            else -> Unit
        }
    }

    fun stop() {
        if (!started) return
        started = false
        ringtone.stop()
        vibrator.stop()
        ringtone.release()
    }

    companion object {
        fun create(
            context: Context,
            overrides: RingOverrides = RingOverrides()
        ): SautiIncomingRing {
            val appContext = context.applicationContext
            return SautiIncomingRing(
                ringtone = MediaPlayerRingtone(appContext, overrides.ringtoneUri),
                vibrator = SystemVibrator(appContext, overrides.vibrationPattern),
                ringerMode = AudioManagerRingerMode(appContext)
            )
        }
    }
}
