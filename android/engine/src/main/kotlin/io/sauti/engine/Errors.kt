package io.sauti.engine

sealed class SautiException(message: String) : Exception(message)

class RoomFullException(message: String) : SautiException(message)

class ServerErrorException(val code: String, message: String) : SautiException(message)

class DisconnectedException(message: String) : SautiException(message)

class VersionMismatchException(val version: Int?) :
    SautiException("protocol version mismatch: peer sent $version, this client speaks $PROTOCOL_VERSION")

fun isFatalServerCode(code: String): Boolean =
    code == "unauthorized" || code == "room_full" || code == "forbidden"
