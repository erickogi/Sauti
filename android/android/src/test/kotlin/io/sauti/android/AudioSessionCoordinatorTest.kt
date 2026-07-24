package io.sauti.android

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.audio.AudioDevice
import io.sauti.android.audio.AudioSessionCoordinator
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioSessionCoordinatorTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun focusLossRaisesInterruptionAndRegainRestores() {
        val coordinator = AudioSessionCoordinator(context)
        val autoMute = mutableListOf<Boolean>()
        coordinator.onInterrupted = { autoMute.add(it) }
        coordinator.start()

        val listener = focusListenerOf(coordinator)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertTrue(coordinator.interrupted.value)
        assertEquals(true, autoMute.first())

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertFalse(coordinator.interrupted.value)
        assertEquals(false, autoMute.last())
    }

    @Test
    @Config(sdk = [28])
    fun legacyRoutingTogglesSpeakerphone() {
        val coordinator = AudioSessionCoordinator(context)
        coordinator.start()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        coordinator.selectDevice(AudioDevice.SPEAKER)
        assertTrue(audioManager.isSpeakerphoneOn)
        assertEquals(AudioDevice.SPEAKER, coordinator.currentDevice.value)

        coordinator.selectDevice(AudioDevice.EARPIECE)
        assertFalse(audioManager.isSpeakerphoneOn)
        assertEquals(AudioDevice.EARPIECE, coordinator.currentDevice.value)
    }

    @Test
    @Config(sdk = [28])
    fun legacyRoutingStartsBluetoothSco() {
        val coordinator = AudioSessionCoordinator(context)
        coordinator.start()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        coordinator.selectDevice(AudioDevice.BLUETOOTH)
        assertTrue(audioManager.isBluetoothScoOn)
        assertEquals(AudioDevice.BLUETOOTH, coordinator.currentDevice.value)
    }

    @Test
    fun startSetsCommunicationModeAndDefaultDevice() {
        val coordinator = AudioSessionCoordinator(context)
        coordinator.start()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertEquals(AudioManager.MODE_IN_COMMUNICATION, audioManager.mode)
        assertEquals(AudioDevice.EARPIECE, coordinator.currentDevice.value)
    }

    @Test
    fun telephonyPathReusesInterruption() {
        val coordinator = AudioSessionCoordinator(context)
        val autoMute = mutableListOf<Boolean>()
        coordinator.onInterrupted = { autoMute.add(it) }
        coordinator.start()
        coordinator.forceInterruption(true)
        assertTrue(coordinator.interrupted.value)
        assertEquals(true, autoMute.first())
    }

    private fun focusListenerOf(coordinator: AudioSessionCoordinator): AudioManager.OnAudioFocusChangeListener {
        val field = AudioSessionCoordinator::class.java.getDeclaredField("focusListener")
        field.isAccessible = true
        return field.get(coordinator) as AudioManager.OnAudioFocusChangeListener
    }
}
