package io.sauti.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoiseSuppressionTest {
    @Test
    fun defaultPolicyResolvesToDisabledOff() {
        val resolved = resolveNoiseSuppression(NoiseSuppressionPolicy())
        assertEquals(
            ResolvedNoiseSuppression(enabled = false, tier = NoiseSuppressionTier.OFF, forceBuiltinNsOff = false),
            resolved
        )
    }

    @Test
    fun defaultPolicyTierIsOff() {
        assertEquals(NoiseSuppressionTier.OFF, NoiseSuppressionPolicy().tier)
    }

    @Test
    fun rnnoiseOptedInAndSupportedEnables() {
        val resolved = resolveNoiseSuppression(
            NoiseSuppressionPolicy(NoiseSuppressionTier.RNNOISE),
            deviceSupportsLearnedNs = true,
            adopterOptIn = true
        )
        assertEquals(
            ResolvedNoiseSuppression(enabled = true, tier = NoiseSuppressionTier.RNNOISE, forceBuiltinNsOff = true),
            resolved
        )
    }

    @Test
    fun deepfilternetOptedInAndSupportedEnables() {
        val resolved = resolveNoiseSuppression(
            NoiseSuppressionPolicy(NoiseSuppressionTier.DEEPFILTERNET),
            deviceSupportsLearnedNs = true,
            adopterOptIn = true
        )
        assertEquals(
            ResolvedNoiseSuppression(enabled = true, tier = NoiseSuppressionTier.DEEPFILTERNET, forceBuiltinNsOff = true),
            resolved
        )
    }

    @Test
    fun rnnoiseNotOptedInDowngradesToOff() {
        val resolved = resolveNoiseSuppression(
            NoiseSuppressionPolicy(NoiseSuppressionTier.RNNOISE),
            deviceSupportsLearnedNs = true,
            adopterOptIn = false
        )
        assertEquals(
            ResolvedNoiseSuppression(enabled = false, tier = NoiseSuppressionTier.OFF, forceBuiltinNsOff = false),
            resolved
        )
    }

    @Test
    fun rnnoiseUnsupportedDeviceDowngradesToOff() {
        val resolved = resolveNoiseSuppression(
            NoiseSuppressionPolicy(NoiseSuppressionTier.RNNOISE),
            deviceSupportsLearnedNs = false,
            adopterOptIn = true
        )
        assertEquals(
            ResolvedNoiseSuppression(enabled = false, tier = NoiseSuppressionTier.OFF, forceBuiltinNsOff = false),
            resolved
        )
    }

    @Test
    fun deepfilternetNotOptedInDowngradesToOff() {
        val resolved = resolveNoiseSuppression(
            NoiseSuppressionPolicy(NoiseSuppressionTier.DEEPFILTERNET),
            deviceSupportsLearnedNs = true,
            adopterOptIn = false
        )
        assertEquals(
            ResolvedNoiseSuppression(enabled = false, tier = NoiseSuppressionTier.OFF, forceBuiltinNsOff = false),
            resolved
        )
    }

    @Test
    fun deepfilternetUnsupportedDeviceDowngradesToOff() {
        val resolved = resolveNoiseSuppression(
            NoiseSuppressionPolicy(NoiseSuppressionTier.DEEPFILTERNET),
            deviceSupportsLearnedNs = false,
            adopterOptIn = true
        )
        assertEquals(
            ResolvedNoiseSuppression(enabled = false, tier = NoiseSuppressionTier.OFF, forceBuiltinNsOff = false),
            resolved
        )
    }

    @Test
    fun offTierNeverEnablesEvenWhenOptedInAndSupported() {
        val resolved = resolveNoiseSuppression(
            NoiseSuppressionPolicy(NoiseSuppressionTier.OFF),
            deviceSupportsLearnedNs = true,
            adopterOptIn = true
        )
        assertEquals(
            ResolvedNoiseSuppression(enabled = false, tier = NoiseSuppressionTier.OFF, forceBuiltinNsOff = false),
            resolved
        )
    }

    @Test
    fun exhaustiveOverTierAndGates() {
        for (tier in NoiseSuppressionTier.entries) {
            for (supported in listOf(true, false)) {
                for (optIn in listOf(true, false)) {
                    val resolved = resolveNoiseSuppression(
                        NoiseSuppressionPolicy(tier),
                        deviceSupportsLearnedNs = supported,
                        adopterOptIn = optIn
                    )
                    val shouldEnable = tier != NoiseSuppressionTier.OFF && supported && optIn
                    if (shouldEnable) {
                        assertTrue(resolved.enabled)
                        assertEquals(tier, resolved.tier)
                        assertTrue(resolved.forceBuiltinNsOff)
                    } else {
                        assertFalse(resolved.enabled)
                        assertEquals(NoiseSuppressionTier.OFF, resolved.tier)
                        assertFalse(resolved.forceBuiltinNsOff)
                    }
                }
            }
        }
    }

    @Test
    fun defaultGatesEnableForNonOffTier() {
        val resolved = resolveNoiseSuppression(NoiseSuppressionPolicy(NoiseSuppressionTier.RNNOISE))
        assertEquals(
            ResolvedNoiseSuppression(enabled = true, tier = NoiseSuppressionTier.RNNOISE, forceBuiltinNsOff = true),
            resolved
        )
    }
}
