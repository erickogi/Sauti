package io.sauti.engine

data class AudioProcessingConfig(
    val hardwareAec: Boolean = true,
    val hardwareNs: Boolean = true,
    val autoGainControl: Boolean? = null,
    val softwareAec: Boolean = false,
    val softwareNs: Boolean = false,
    val highpassFilter: Boolean? = null
)

fun audioConstraints(config: AudioProcessingConfig): List<Pair<String, String>> {
    val pairs = mutableListOf<Pair<String, String>>()
    if (config.softwareAec) pairs.add("googEchoCancellation" to "true")
    if (config.softwareNs) pairs.add("googNoiseSuppression" to "true")
    if (config.autoGainControl != null) {
        pairs.add("googAutoGainControl" to if (config.autoGainControl) "true" else "false")
    }
    if (config.highpassFilter != null) {
        pairs.add("googHighpassFilter" to if (config.highpassFilter) "true" else "false")
    }
    return pairs
}
