package io.sauti.android.ring

interface RingbackTone {
    fun start()
    fun stop()
    fun release()
}

interface RingtonePlayer {
    fun start()
    fun stop()
    fun release()
}

interface CallVibrator {
    fun start()
    fun stop()
}

interface RingerModeProvider {
    fun current(): RingerMode
}
