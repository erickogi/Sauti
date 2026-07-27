package io.sauti.android.ring

import android.content.Context
import android.media.ToneGenerator
import android.net.Uri
import io.sauti.engine.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

val DEFAULT_VIBRATION_PATTERN: LongArray = longArrayOf(0L, 800L, 1000L)

data class RingOverrides(
    val ringtoneUri: Uri? = null,
    val ringbackToneType: Int = ToneGenerator.TONE_SUP_RINGTONE,
    val vibrationPattern: LongArray = DEFAULT_VIBRATION_PATTERN
)

class SautiRinger internal constructor(
    private val scope: CoroutineScope,
    private val ringback: RingbackTone,
    private val ringtone: RingtonePlayer,
    private val vibrator: CallVibrator,
    private val ringerMode: RingerModeProvider
) {

    private var job: Job? = null
    private var lastMode: RingMode? = null

    fun start(role: CallRole, state: StateFlow<CallState>, selfParticipantId: String? = null) {
        job?.cancel()
        lastMode = null
        job = scope.launch {
            state.collect { snapshot ->
                val mode = RingReducer.reduce(role, snapshot, selfParticipantId, ringerMode.current())
                if (mode != lastMode) {
                    lastMode = mode
                    apply(mode)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        lastMode = null
        ringback.stop()
        ringtone.stop()
        vibrator.stop()
        ringback.release()
        ringtone.release()
    }

    private fun apply(mode: RingMode) {
        when (mode) {
            RingMode.Silent -> {
                ringback.stop()
                ringtone.stop()
                vibrator.stop()
            }
            RingMode.Ringback -> {
                ringback.start()
                ringtone.stop()
                vibrator.stop()
            }
            is RingMode.Incoming -> {
                ringback.stop()
                if (mode.sound) ringtone.start() else ringtone.stop()
                vibrator.start()
            }
        }
    }

    companion object {
        fun create(
            context: Context,
            scope: CoroutineScope,
            overrides: RingOverrides = RingOverrides()
        ): SautiRinger {
            val appContext = context.applicationContext
            return SautiRinger(
                scope = scope,
                ringback = ToneGeneratorRingback(overrides.ringbackToneType),
                ringtone = MediaPlayerRingtone(appContext, overrides.ringtoneUri),
                vibrator = SystemVibrator(appContext, overrides.vibrationPattern),
                ringerMode = AudioManagerRingerMode(appContext)
            )
        }
    }
}
