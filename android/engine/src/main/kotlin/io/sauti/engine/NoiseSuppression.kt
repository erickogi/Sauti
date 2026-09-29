package io.sauti.engine

enum class NoiseSuppressionTier { OFF, RNNOISE, DEEPFILTERNET }

data class NoiseSuppressionPolicy(val tier: NoiseSuppressionTier = NoiseSuppressionTier.OFF)

data class ResolvedNoiseSuppression(
    val enabled: Boolean,
    val tier: NoiseSuppressionTier,
    val forceBuiltinNsOff: Boolean
)

fun resolveNoiseSuppression(
    policy: NoiseSuppressionPolicy,
    deviceSupportsLearnedNs: Boolean = true,
    adopterOptIn: Boolean = true
): ResolvedNoiseSuppression {
    val enabled = policy.tier != NoiseSuppressionTier.OFF && adopterOptIn && deviceSupportsLearnedNs
    return if (enabled) {
        ResolvedNoiseSuppression(enabled = true, tier = policy.tier, forceBuiltinNsOff = true)
    } else {
        ResolvedNoiseSuppression(enabled = false, tier = NoiseSuppressionTier.OFF, forceBuiltinNsOff = false)
    }
}
