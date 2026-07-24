package io.sauti.engine

object Politeness {
    fun politePeer(a: String, b: String): String = if (a <= b) a else b

    fun isPolite(selfId: String, peerId: String): Boolean = politePeer(selfId, peerId) == selfId
}
