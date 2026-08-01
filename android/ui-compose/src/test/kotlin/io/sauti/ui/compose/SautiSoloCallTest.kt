package io.sauti.ui.compose

import io.sauti.android.audio.AudioDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SautiSoloCallTest {

    @Test
    fun avatarInitialTakesFirstCharacterUppercased() {
        assertEquals("A", avatarInitial("amina"))
        assertEquals("Z", avatarInitial("Zawadi"))
    }

    @Test
    fun avatarInitialTrimsLeadingWhitespace() {
        assertEquals("B", avatarInitial("   bakari"))
    }

    @Test
    fun avatarInitialIsEmptyForBlankName() {
        assertEquals("", avatarInitial(""))
        assertEquals("", avatarInitial("   "))
    }

    @Test
    fun audioButtonActiveIsFalseForEarpiece() {
        assertFalse(audioButtonActive(AudioDevice.EARPIECE))
    }

    @Test
    fun audioButtonActiveIsTrueForOtherDevices() {
        assertTrue(audioButtonActive(AudioDevice.SPEAKER))
        assertTrue(audioButtonActive(AudioDevice.BLUETOOTH))
        assertTrue(audioButtonActive(AudioDevice.WIRED_HEADSET))
    }

    @Test
    fun audioDeviceOrderFiltersToAvailableInEnumOrder() {
        val available = setOf(AudioDevice.WIRED_HEADSET, AudioDevice.EARPIECE, AudioDevice.SPEAKER)

        assertEquals(
            listOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER, AudioDevice.WIRED_HEADSET),
            audioDeviceOrder(available)
        )
    }
}
