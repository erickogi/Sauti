package io.sauti.android.ring

enum class CallRole {
    OUTGOING,
    INCOMING
}

enum class RingerMode {
    SILENT,
    VIBRATE,
    NORMAL
}

sealed interface RingMode {
    data object Silent : RingMode
    data object Ringback : RingMode
    data class Incoming(val sound: Boolean) : RingMode
}
