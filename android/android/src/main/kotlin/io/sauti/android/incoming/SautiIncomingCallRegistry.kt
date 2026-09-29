package io.sauti.android.incoming

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object SautiIncomingCallRegistry {

    fun interface Finisher {
        fun finish()
    }

    private const val MAX_CANCELLED = 256

    private val active: MutableSet<String> = Collections.synchronizedSet(mutableSetOf<String>())
    private val cancelled: LinkedHashSet<String> = LinkedHashSet()
    private val finishers: ConcurrentHashMap<String, Finisher> = ConcurrentHashMap()

    fun shouldPresent(callId: String): Boolean {
        if (callId.isBlank()) return false
        if (isCancelled(callId)) return false
        return active.add(callId)
    }

    fun release(callId: String) {
        active.remove(callId)
        clearCancelled(callId)
    }

    fun register(callId: String, finisher: Finisher) {
        if (callId.isBlank()) return
        if (isCancelled(callId)) {
            finisher.finish()
            return
        }
        finishers[callId] = finisher
    }

    fun unregister(callId: String, finisher: Finisher) {
        finishers.remove(callId, finisher)
    }

    fun cancel(callId: String) {
        if (callId.isBlank()) return
        markCancelled(callId)
        finishers.remove(callId)?.finish()
    }

    private fun markCancelled(callId: String) {
        synchronized(cancelled) {
            cancelled.add(callId)
            while (cancelled.size > MAX_CANCELLED) {
                cancelled.remove(cancelled.iterator().next())
            }
        }
    }

    private fun isCancelled(callId: String): Boolean =
        synchronized(cancelled) { cancelled.contains(callId) }

    private fun clearCancelled(callId: String) {
        synchronized(cancelled) { cancelled.remove(callId) }
    }

    fun finish(callId: String) {
        finishers.remove(callId)?.finish()
    }
}
