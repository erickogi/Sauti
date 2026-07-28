package io.sauti.android.rtc

import io.sauti.engine.AudioProcessingConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebRtcFactoryAudioConstraintsTest {
    @Test
    fun defaultConfigProducesEmptyConstraints() {
        val constraints = buildAudioConstraints(AudioProcessingConfig())
        assertTrue(constraints.optional.isEmpty())
        assertTrue(constraints.mandatory.isEmpty())
    }

    @Test
    fun optInPairsLandOnOptionalListInOrder() {
        val constraints = buildAudioConstraints(
            AudioProcessingConfig(softwareAec = true, softwareNs = true, autoGainControl = true)
        )
        assertTrue(constraints.mandatory.isEmpty())
        assertEquals(3, constraints.optional.size)
        assertEquals("googEchoCancellation", constraints.optional[0].key)
        assertEquals("true", constraints.optional[0].value)
        assertEquals("googNoiseSuppression", constraints.optional[1].key)
        assertEquals("true", constraints.optional[1].value)
        assertEquals("googAutoGainControl", constraints.optional[2].key)
        assertEquals("true", constraints.optional[2].value)
    }

    @Test
    fun hardwareFlagsDoNotLeakIntoConstraints() {
        val constraints = buildAudioConstraints(
            AudioProcessingConfig(hardwareAec = false, hardwareNs = false)
        )
        assertTrue(constraints.optional.isEmpty())
        assertTrue(constraints.mandatory.isEmpty())
    }

    @Test
    fun autoGainControlFalseIsWrittenAsFalse() {
        val constraints = buildAudioConstraints(AudioProcessingConfig(autoGainControl = false))
        assertEquals(1, constraints.optional.size)
        assertEquals("googAutoGainControl", constraints.optional[0].key)
        assertEquals("false", constraints.optional[0].value)
    }
}
