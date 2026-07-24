package io.sauti.engine

class DurationClock(private val now: () -> Long) {
    private var clockOffset: Long = 0
    private var startedAt: Long? = null
    private var offsetCaptured = false

    fun captureOffset(serverNow: Long) {
        if (offsetCaptured) return
        clockOffset = serverNow - now()
        offsetCaptured = true
    }

    fun setStartedAt(value: Long?) {
        startedAt = value
    }

    fun startedAt(): Long? = startedAt

    fun elapsedMs(): Long {
        val anchor = startedAt ?: return 0
        val elapsed = now() + clockOffset - anchor
        return if (elapsed > 0) elapsed else 0
    }
}
