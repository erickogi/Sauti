package io.sauti.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioProcessingTest {
    @Test
    fun defaultConfigYieldsEmptyConstraints() {
        assertEquals(emptyList<Pair<String, String>>(), audioConstraints(AudioProcessingConfig()))
    }

    @Test
    fun defaultConfigMatchesTodaysHardwareLiterals() {
        val config = AudioProcessingConfig()
        assertEquals(true, config.hardwareAec)
        assertEquals(true, config.hardwareNs)
        assertEquals(null, config.autoGainControl)
        assertEquals(false, config.softwareAec)
        assertEquals(false, config.softwareNs)
        assertEquals(null, config.highpassFilter)
    }

    @Test
    fun hardwareFlagsNeverEnterConstraints() {
        assertEquals(emptyList<Pair<String, String>>(), audioConstraints(AudioProcessingConfig(hardwareAec = false)))
        assertEquals(emptyList<Pair<String, String>>(), audioConstraints(AudioProcessingConfig(hardwareNs = false)))
        assertEquals(
            emptyList<Pair<String, String>>(),
            audioConstraints(AudioProcessingConfig(hardwareAec = false, hardwareNs = false))
        )
    }

    @Test
    fun softwareAecEmitsEchoCancellation() {
        assertEquals(
            listOf("googEchoCancellation" to "true"),
            audioConstraints(AudioProcessingConfig(softwareAec = true))
        )
    }

    @Test
    fun softwareNsEmitsNoiseSuppression() {
        assertEquals(
            listOf("googNoiseSuppression" to "true"),
            audioConstraints(AudioProcessingConfig(softwareNs = true))
        )
    }

    @Test
    fun softwareFlagsOmitWhenFalse() {
        assertEquals(
            emptyList<Pair<String, String>>(),
            audioConstraints(AudioProcessingConfig(softwareAec = false, softwareNs = false))
        )
    }

    @Test
    fun softwareAecAndNsKeepFixedOrder() {
        assertEquals(
            listOf("googEchoCancellation" to "true", "googNoiseSuppression" to "true"),
            audioConstraints(AudioProcessingConfig(softwareAec = true, softwareNs = true))
        )
    }

    @Test
    fun autoGainControlTrueEmitsTrue() {
        assertEquals(
            listOf("googAutoGainControl" to "true"),
            audioConstraints(AudioProcessingConfig(autoGainControl = true))
        )
    }

    @Test
    fun autoGainControlFalseEmitsFalse() {
        assertEquals(
            listOf("googAutoGainControl" to "false"),
            audioConstraints(AudioProcessingConfig(autoGainControl = false))
        )
    }

    @Test
    fun autoGainControlNullOmitsPair() {
        assertEquals(
            emptyList<Pair<String, String>>(),
            audioConstraints(AudioProcessingConfig(autoGainControl = null))
        )
    }

    @Test
    fun highpassFilterTrueEmitsTrue() {
        assertEquals(
            listOf("googHighpassFilter" to "true"),
            audioConstraints(AudioProcessingConfig(highpassFilter = true))
        )
    }

    @Test
    fun highpassFilterFalseEmitsFalse() {
        assertEquals(
            listOf("googHighpassFilter" to "false"),
            audioConstraints(AudioProcessingConfig(highpassFilter = false))
        )
    }

    @Test
    fun highpassFilterNullOmitsPair() {
        assertEquals(
            emptyList<Pair<String, String>>(),
            audioConstraints(AudioProcessingConfig(highpassFilter = null))
        )
    }

    @Test
    fun allFlagsSetKeepFullFixedOrder() {
        assertEquals(
            listOf(
                "googEchoCancellation" to "true",
                "googNoiseSuppression" to "true",
                "googAutoGainControl" to "false",
                "googHighpassFilter" to "true"
            ),
            audioConstraints(
                AudioProcessingConfig(
                    hardwareAec = false,
                    hardwareNs = false,
                    autoGainControl = false,
                    softwareAec = true,
                    softwareNs = true,
                    highpassFilter = true
                )
            )
        )
    }
}
