package io.sauti.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

const val PROTOCOL_VERSION = 1

sealed interface ServerFrame {
    data class Ready(
        val self: Participant,
        val peers: List<Participant>,
        val room: RoomInfo,
        val iceServers: List<IceServer>,
        val serverNow: Long,
        val resumed: Boolean
    ) : ServerFrame

    data class ParticipantJoined(val participant: Participant) : ServerFrame
    data class ParticipantStateChanged(val participantId: String, val state: PartialState) : ServerFrame
    data class ParticipantUnreachable(val participantId: String) : ServerFrame
    data class ParticipantLeft(val participantId: String) : ServerFrame
    data class RoomStarted(val startedAt: Long) : ServerFrame
    data class ErrorFrame(val code: String, val message: String) : ServerFrame
    data class Offer(val from: String, val sdp: String) : ServerFrame
    data class Answer(val from: String, val sdp: String) : ServerFrame
    data class Ice(val from: String, val candidate: IceCandidate) : ServerFrame
}

sealed interface FrameParseResult {
    data class Parsed(val frame: ServerFrame) : FrameParseResult
    data class VersionMismatch(val version: Int?) : FrameParseResult
    object Invalid : FrameParseResult
}

object FrameCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeJoin(token: String): String = buildJsonObject {
        put("v", PROTOCOL_VERSION)
        put("type", "join")
        put("token", token)
    }.toString()

    fun encodeLeave(): String = buildJsonObject {
        put("v", PROTOCOL_VERSION)
        put("type", "leave")
    }.toString()

    fun encodeState(muted: Boolean?, onHold: Boolean?): String = buildJsonObject {
        put("v", PROTOCOL_VERSION)
        put("type", "state")
        put("state", buildJsonObject {
            if (muted != null) put("muted", muted)
            if (onHold != null) put("onHold", onHold)
        })
    }.toString()

    fun encodeOffer(to: String, sdp: String): String = buildJsonObject {
        put("v", PROTOCOL_VERSION)
        put("type", "offer")
        put("to", to)
        put("sdp", sdp)
    }.toString()

    fun encodeAnswer(to: String, sdp: String): String = buildJsonObject {
        put("v", PROTOCOL_VERSION)
        put("type", "answer")
        put("to", to)
        put("sdp", sdp)
    }.toString()

    fun encodeIce(to: String, candidate: IceCandidate): String = buildJsonObject {
        put("v", PROTOCOL_VERSION)
        put("type", "ice")
        put("to", to)
        put("candidate", encodeCandidate(candidate))
    }.toString()

    private fun encodeCandidate(candidate: IceCandidate): JsonObject = buildJsonObject {
        put("candidate", candidate.candidate)
        if (candidate.sdpMid != null) put("sdpMid", candidate.sdpMid) else put("sdpMid", JsonNull)
        if (candidate.sdpMLineIndex != null) put("sdpMLineIndex", candidate.sdpMLineIndex) else put("sdpMLineIndex", JsonNull)
    }

    fun parse(text: String): FrameParseResult {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (error: Exception) {
            return FrameParseResult.Invalid
        }

        val version = root["v"]?.jsonPrimitive?.content?.toIntOrNull()
        if (version != PROTOCOL_VERSION) {
            return FrameParseResult.VersionMismatch(version)
        }

        val type = root["type"]?.jsonPrimitive?.content ?: return FrameParseResult.Invalid

        val frame = when (type) {
            "ready" -> parseReady(root)
            "participant-joined" -> ServerFrame.ParticipantJoined(parseParticipant(root.getValue("participant").jsonObject))
            "participant-state" -> ServerFrame.ParticipantStateChanged(
                root.getValue("participantId").jsonPrimitive.content,
                parsePartialState(root.getValue("state").jsonObject)
            )
            "participant-unreachable" -> ServerFrame.ParticipantUnreachable(root.getValue("participantId").jsonPrimitive.content)
            "participant-left" -> ServerFrame.ParticipantLeft(root.getValue("participantId").jsonPrimitive.content)
            "room-started" -> ServerFrame.RoomStarted(root.getValue("startedAt").jsonPrimitive.content.toLong())
            "error" -> ServerFrame.ErrorFrame(
                root.getValue("code").jsonPrimitive.content,
                root.getValue("message").jsonPrimitive.content
            )
            "offer" -> ServerFrame.Offer(root.getValue("from").jsonPrimitive.content, root.getValue("sdp").jsonPrimitive.content)
            "answer" -> ServerFrame.Answer(root.getValue("from").jsonPrimitive.content, root.getValue("sdp").jsonPrimitive.content)
            "ice" -> ServerFrame.Ice(
                root.getValue("from").jsonPrimitive.content,
                parseCandidate(root.getValue("candidate").jsonObject)
            )
            else -> return FrameParseResult.Invalid
        }
        return FrameParseResult.Parsed(frame)
    }

    private fun parseReady(root: JsonObject): ServerFrame.Ready {
        val roomObj = root.getValue("room").jsonObject
        val startedAt = roomObj["startedAt"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.toLong()
        return ServerFrame.Ready(
            self = parseParticipant(root.getValue("self").jsonObject),
            peers = root.getValue("peers").jsonArray.map { parseParticipant(it.jsonObject) },
            room = RoomInfo(
                roomId = roomObj.getValue("roomId").jsonPrimitive.content,
                startedAt = startedAt,
                maxParticipants = roomObj.getValue("maxParticipants").jsonPrimitive.content.toInt()
            ),
            iceServers = (root["iceServers"]?.jsonArray ?: buildJsonArray { }).map { parseIceServer(it.jsonObject) },
            serverNow = root.getValue("serverNow").jsonPrimitive.content.toLong(),
            resumed = root["resumed"]?.jsonPrimitive?.content?.toBoolean() ?: false
        )
    }

    private fun parseParticipant(obj: JsonObject): Participant {
        val stateObj = obj["state"]?.jsonObject
        return Participant(
            participantId = obj.getValue("participantId").jsonPrimitive.content,
            joinedAt = obj["joinedAt"]?.jsonPrimitive?.content?.toLong() ?: 0L,
            metadata = obj["metadata"]?.jsonObject?.let { readMetadata(it) } ?: emptyMap(),
            connectionState = parseConnectionState(obj["connectionState"]?.jsonPrimitive?.content),
            state = ParticipantState(
                muted = stateObj?.get("muted")?.jsonPrimitive?.content?.toBoolean() ?: false,
                onHold = stateObj?.get("onHold")?.jsonPrimitive?.content?.toBoolean() ?: false
            )
        )
    }

    private fun readMetadata(obj: JsonObject): Map<String, JsonElement> = obj.toMap()

    private fun parsePartialState(obj: JsonObject): PartialState = PartialState(
        muted = obj["muted"]?.jsonPrimitive?.content?.toBoolean(),
        onHold = obj["onHold"]?.jsonPrimitive?.content?.toBoolean()
    )

    private fun parseConnectionState(raw: String?): ConnectionState = when (raw) {
        "connecting" -> ConnectionState.CONNECTING
        "connected" -> ConnectionState.CONNECTED
        "reconnecting" -> ConnectionState.RECONNECTING
        "unreachable" -> ConnectionState.UNREACHABLE
        "left" -> ConnectionState.LEFT
        else -> ConnectionState.CONNECTING
    }

    private fun parseIceServer(obj: JsonObject): IceServer {
        val urlsElement = obj["urls"]
        val urls = when {
            urlsElement is JsonPrimitive -> listOf(urlsElement.content)
            urlsElement != null -> urlsElement.jsonArray.map { it.jsonPrimitive.content }
            else -> emptyList()
        }
        return IceServer(
            urls = urls,
            username = obj["username"]?.jsonPrimitive?.content,
            credential = obj["credential"]?.jsonPrimitive?.content
        )
    }

    private fun parseCandidate(obj: JsonObject): IceCandidate = IceCandidate(
        candidate = obj.getValue("candidate").jsonPrimitive.content,
        sdpMid = obj["sdpMid"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
        sdpMLineIndex = obj["sdpMLineIndex"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.toInt()
    )
}
