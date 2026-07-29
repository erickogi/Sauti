package io.sauti.android.telecom

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.audio.AudioDevice
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TelecomAudioControllerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Test
    fun startNeverGrabsCommunicationMode() {
        val controller = TelecomAudioController(context)
        controller.start()
        assertEquals(AudioManager.MODE_NORMAL, audioManager.mode)
    }

    @Test
    fun selectDeviceUpdatesStateWithoutTouchingRouting() {
        val controller = TelecomAudioController(context)
        controller.start()

        controller.selectDevice(AudioDevice.SPEAKER)

        assertEquals(AudioDevice.SPEAKER, controller.currentDevice.value)
        assertFalse(audioManager.isSpeakerphoneOn)
        assertEquals(AudioManager.MODE_NORMAL, audioManager.mode)
    }

    @Test
    fun availableDevicesAlwaysIncludeEarpieceAndSpeaker() {
        val controller = TelecomAudioController(context)
        controller.start()
        assertTrue(controller.availableDevices.value.contains(AudioDevice.EARPIECE))
        assertTrue(controller.availableDevices.value.contains(AudioDevice.SPEAKER))
    }

    @Test
    fun forceInterruptionUpdatesFlagWithoutAudioModeChange() {
        val controller = TelecomAudioController(context)
        val signals = mutableListOf<Boolean>()
        controller.onInterrupted = { signals.add(it) }
        controller.start()

        controller.forceInterruption(true)

        assertTrue(controller.interrupted.value)
        assertEquals(listOf(true), signals)
        assertEquals(AudioManager.MODE_NORMAL, audioManager.mode)
    }

    @Test
    fun stopClearsInterruptionAndLeavesModeNormal() {
        val controller = TelecomAudioController(context)
        controller.start()
        controller.forceInterruption(true)

        controller.stop()

        assertFalse(controller.interrupted.value)
        assertEquals(AudioManager.MODE_NORMAL, audioManager.mode)
    }
}
