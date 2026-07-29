package io.sauti.android.proximity

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import io.sauti.android.audio.AudioDevice
import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ProximityController(
    context: Context,
    private val scope: CoroutineScope,
    private val callState: StateFlow<CallState>,
    private val currentDevice: StateFlow<AudioDevice>
) {
    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val wakeLock: PowerManager.WakeLock? =
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, WAKE_LOCK_TAG)
        } else {
            null
        }

    private val nearState = MutableStateFlow(false)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val range = proximitySensor?.maximumRange ?: return
            val value = event.values.firstOrNull() ?: return
            nearState.value = value < range
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private var job: Job? = null
    private var registered = false

    fun start() {
        if (job != null) return
        proximitySensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            registered = true
        }
        job = scope.launch {
            combine(callState, currentDevice, nearState) { state, device, near ->
                ProximityReducer.reduce(state.phase == CallPhase.CONNECTED, near, device)
            }.collect { apply(it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (registered) {
            sensorManager.unregisterListener(listener)
            registered = false
        }
        nearState.value = false
        release()
    }

    private fun apply(intent: ScreenIntent) {
        when (intent) {
            ScreenIntent.Off -> acquire()
            ScreenIntent.On -> release()
        }
    }

    private fun acquire() {
        val lock = wakeLock ?: return
        if (!lock.isHeld) lock.acquire()
    }

    private fun release() {
        val lock = wakeLock ?: return
        if (lock.isHeld) lock.release()
    }

    private companion object {
        const val WAKE_LOCK_TAG = "io.sauti:proximity"
    }
}
