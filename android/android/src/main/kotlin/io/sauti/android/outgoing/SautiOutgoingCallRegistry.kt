package io.sauti.android.outgoing

object SautiOutgoingCallRegistry {

    fun interface Finisher {
        fun finish()
    }

    private val lock = Any()
    private var current: String? = null
    private var isConnected = false
    private var hostFinisher: Finisher? = null
    private var pendingFinish = false

    val currentCallId: String? get() = synchronized(lock) { current }

    val connected: Boolean get() = synchronized(lock) { isConnected }

    fun begin(callId: String) = synchronized(lock) {
        current = callId
        isConnected = false
    }

    fun markConnected() = synchronized(lock) {
        isConnected = true
    }

    fun clear() = synchronized(lock) {
        current = null
        isConnected = false
        pendingFinish = false
    }

    fun matchesUnconnected(callId: String): Boolean = synchronized(lock) {
        callId.isNotBlank() && callId == current && !isConnected
    }

    fun registerFinisher(finisher: Finisher) {
        val fireNow = synchronized(lock) {
            if (pendingFinish) {
                pendingFinish = false
                true
            } else {
                hostFinisher = finisher
                false
            }
        }
        if (fireNow) finisher.finish()
    }

    fun unregisterFinisher(finisher: Finisher) = synchronized(lock) {
        if (hostFinisher === finisher) hostFinisher = null
    }

    fun finishHost() {
        val finisher = synchronized(lock) {
            val existing = hostFinisher
            if (existing != null) {
                hostFinisher = null
                existing
            } else {
                pendingFinish = true
                null
            }
        }
        finisher?.finish()
    }
}
