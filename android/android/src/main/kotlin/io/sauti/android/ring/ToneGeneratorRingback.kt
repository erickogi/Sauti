package io.sauti.android.ring

import android.media.AudioManager
import android.media.ToneGenerator

internal class ToneGeneratorRingback(
    private val toneType: Int
) : RingbackTone {

    private var generator: ToneGenerator? = null
    private var active = false

    override fun start() {
        if (active) return
        active = true
        val tone = generator ?: runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, RINGBACK_VOLUME)
        }.getOrNull()
        generator = tone
        tone?.startTone(toneType)
    }

    override fun stop() {
        if (!active) return
        active = false
        generator?.stopTone()
    }

    override fun release() {
        active = false
        generator?.release()
        generator = null
    }
}

private const val RINGBACK_VOLUME = 80
