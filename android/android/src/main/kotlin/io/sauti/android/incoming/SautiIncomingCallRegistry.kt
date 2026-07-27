package io.sauti.android.incoming

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object SautiIncomingCallRegistry {

    fun interface Finisher {
        fun finish()
    }

    private val active: MutableSet<String> = Collections.synchronizedSet(mutableSetOf<String>())
    private val finishers: ConcurrentHashMap<String, Finisher> = ConcurrentHashMap()

    fun shouldPresent(callId: String): Boolean {
        if (callId.isBlank()) return false
        return active.add(callId)
    }

    fun release(callId: String) {
        active.remove(callId)
    }

    fun register(callId: String, finisher: Finisher) {
        if (callId.isBlank()) return
        finishers[callId] = finisher
    }

    fun unregister(callId: String, finisher: Finisher) {
        finishers.remove(callId, finisher)
    }

    fun finish(callId: String) {
        finishers.remove(callId)?.finish()
    }
}
