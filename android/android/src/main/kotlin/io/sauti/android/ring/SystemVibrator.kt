package io.sauti.android.ring

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

internal class SystemVibrator(
    private val context: Context,
    private val pattern: LongArray
) : CallVibrator {

    private var active: Vibrator? = null

    override fun start() {
        if (active != null) return
        val vibrator = resolve() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, VIBRATION_REPEAT))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, VIBRATION_REPEAT)
        }
        active = vibrator
    }

    override fun stop() {
        active?.cancel()
        active = null
    }

    private fun resolve(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}

private const val VIBRATION_REPEAT = 0
