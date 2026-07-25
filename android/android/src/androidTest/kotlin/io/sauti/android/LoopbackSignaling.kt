package io.sauti.android

import io.sauti.engine.SignalingTransport
import io.sauti.engine.SignalingTransportFactory
import io.sauti.engine.TransportCallbacks
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class LoopbackSignaling(
    private val roomId: String,
    private val maxParticipants: Int = 3
) : SignalingTransportFactory {

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val members = LinkedHashMap<String, Member>()

    private inner class Member(
        val callbacks: TransportCallbacks
    ) : SignalingTransport {
        var participantId: String = ""
        @Volatile
        var open = true

        override fun send(text: String) {
            handleClientFrame(this, text)
        }

        override fun close() {
            handleLeave(this)
            open = false
        }

        fun deliver(text: String) {
            if (open) callbacks.onMessage(text)
        }
    }

    override fun connect(url: String, callbacks: TransportCallbacks): SignalingTransport {
        val member = Member(callbacks)
        callbacks.onOpen()
        return member
    }

    private fun handleClientFrame(member: Member, text: String) {
        val root = json.parseToJsonElement(text).jsonObject
        when (root["type"]?.jsonPrimitive?.content) {
            "join" -> handleJoin(member, root.getValue("token").jsonPrimitive.content)
            "offer" -> relay(member, "offer", root)
            "answer" -> relay(member, "answer", root)
            "ice" -> relayIce(member, root)
            "state" -> broadcastState(member, root)
            "leave" -> handleLeave(member)
            else -> Unit
        }
    }

    private fun handleJoin(member: Member, token: String) {
        val others: List<Member>
        synchronized(lock) {
            member.participantId = token
            others = members.values.filter { it.open }
            members[token] = member
        }
        member.deliver(readyFrame(member.participantId, others))
        val announcement = participantFrame(member.participantId)
        for (other in others) other.deliver(announcement)
    }

    private fun relay(member: Member, type: String, root: JsonObject) {
        val target = targetOf(root) ?: return
        val payload = buildJsonObject {
            put("v", 1)
            put("type", type)
            put("from", member.participantId)
            put("sdp", root.getValue("sdp").jsonPrimitive.content)
        }
        target.deliver(payload.toString())
    }

    private fun relayIce(member: Member, root: JsonObject) {
        val target = targetOf(root) ?: return
        val payload = buildJsonObject {
            put("v", 1)
            put("type", "ice")
            put("from", member.participantId)
            put("candidate", root.getValue("candidate").jsonObject)
        }
        target.deliver(payload.toString())
    }

    private fun broadcastState(member: Member, root: JsonObject) {
        val others: List<Member>
        synchronized(lock) {
            others = members.values.filter { it.open && it.participantId != member.participantId }
        }
        val payload = buildJsonObject {
            put("v", 1)
            put("type", "participant-state")
            put("participantId", member.participantId)
            put("state", root.getValue("state").jsonObject)
        }
        for (other in others) other.deliver(payload.toString())
    }

    private fun handleLeave(member: Member) {
        val others: List<Member>
        synchronized(lock) {
            if (member.participantId.isEmpty()) return
            members.remove(member.participantId)
            others = members.values.filter { it.open }
        }
        val payload = buildJsonObject {
            put("v", 1)
            put("type", "participant-left")
            put("participantId", member.participantId)
        }
        for (other in others) other.deliver(payload.toString())
    }

    private fun targetOf(root: JsonObject): Member? {
        val to = root["to"]?.jsonPrimitive?.content ?: return null
        return synchronized(lock) { members[to] }
    }

    private fun readyFrame(selfId: String, peers: List<Member>): String = buildJsonObject {
        put("v", 1)
        put("type", "ready")
        put("self", participantObject(selfId))
        put("peers", buildJsonArray { for (peer in peers) add(participantObject(peer.participantId)) })
        put("room", buildJsonObject {
            put("roomId", roomId)
            put("startedAt", JsonNull)
            put("maxParticipants", maxParticipants)
        })
        put("iceServers", buildJsonArray { })
        put("serverNow", System.currentTimeMillis())
        put("resumed", false)
    }.toString()

    private fun participantFrame(participantId: String): String = buildJsonObject {
        put("v", 1)
        put("type", "participant-joined")
        put("participant", participantObject(participantId))
    }.toString()

    private fun participantObject(participantId: String): JsonObject = buildJsonObject {
        put("participantId", participantId)
        put("joinedAt", System.currentTimeMillis())
        put("metadata", buildJsonObject { })
        put("connectionState", "connected")
        put("state", buildJsonObject {
            put("muted", false)
            put("onHold", false)
        })
    }
}
