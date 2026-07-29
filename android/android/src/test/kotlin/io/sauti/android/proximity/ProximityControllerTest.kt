package io.sauti.android.proximity

import android.content.Context
import android.hardware.Sensor
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.audio.AudioDevice
import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProximityControllerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val powerManager
        get() = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val sensorManager
        get() = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager

    private fun installProximitySensor(range: Float = 5f): Sensor {
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_PROXIMITY)
        shadowOf(sensor).setMaximumRange(range)
        shadowOf(sensorManager).addSensor(Sensor.TYPE_PROXIMITY, sensor)
        return sensor
    }

    private fun sendProximity(value: Float) {
        val event = ShadowSensorManager.createSensorEvent(1, Sensor.TYPE_PROXIMITY)
        event.values[0] = value
        shadowOf(sensorManager).sendSensorEventToListeners(event)
    }

    private fun build(
        phase: CallPhase = CallPhase.CONNECTED,
        device: AudioDevice = AudioDevice.EARPIECE
    ): ProximityController {
        shadowOf(powerManager)
            .setIsWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, true)
        return ProximityController(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            callState = MutableStateFlow(CallState(phase = phase)),
            currentDevice = MutableStateFlow(device)
        )
    }

    @Test
    fun startRegistersProximityListener() {
        installProximitySensor()
        val controller = build()

        controller.start()

        assertTrue(shadowOf(sensorManager).listeners.isNotEmpty())
    }

    @Test
    fun nearOnEarpieceDuringActiveCallHoldsWakeLock() {
        installProximitySensor()
        val controller = build()

        controller.start()
        sendProximity(0f)

        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun awayReleasesWakeLock() {
        installProximitySensor(range = 5f)
        val controller = build()

        controller.start()
        sendProximity(0f)
        sendProximity(5f)

        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun stopReleasesWakeLockAndUnregistersListener() {
        installProximitySensor()
        val controller = build()

        controller.start()
        sendProximity(0f)
        controller.stop()

        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
        assertTrue(shadowOf(sensorManager).listeners.isEmpty())
    }

    @Test
    fun idleCallNeverHoldsWakeLock() {
        installProximitySensor()
        val controller = build(phase = CallPhase.IDLE)

        controller.start()
        sendProximity(0f)

        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }
}
