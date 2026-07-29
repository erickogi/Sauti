package io.sauti.android.telephony

enum class InterruptionMode {
    Mute,
    Hold
}

data class InterruptionPolicy(
    val mode: InterruptionMode = InterruptionMode.Mute
)

data class UserAudioIntent(
    val muted: Boolean,
    val onHold: Boolean
)

data class InterruptionEffect(
    val muted: Boolean,
    val onHold: Boolean?
)

object InterruptionReducer {
    fun reduce(
        interrupted: Boolean,
        saved: UserAudioIntent,
        policy: InterruptionPolicy
    ): InterruptionEffect = when (policy.mode) {
        InterruptionMode.Mute ->
            if (interrupted) {
                InterruptionEffect(muted = true, onHold = null)
            } else {
                InterruptionEffect(muted = saved.muted, onHold = null)
            }
        InterruptionMode.Hold ->
            if (interrupted) {
                InterruptionEffect(muted = true, onHold = true)
            } else {
                InterruptionEffect(muted = saved.muted, onHold = saved.onHold)
            }
    }
}
