package io.sauti.android.ring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RecordingRingtonePlayer : RingtonePlayer {
    val events = mutableListOf<String>()
    override fun start() { events += "start" }
    override fun stop() { events += "stop" }
    override fun release() { events += "release" }
}

private class RecordingCallVibrator : CallVibrator {
    val events = mutableListOf<String>()
    override fun start() { events += "start" }
    override fun stop() { events += "stop" }
}

private class FixedRingerModeProvider(private val mode: RingerMode) : RingerModeProvider {
    override fun current(): RingerMode = mode
}

class SautiIncomingRingTest {

    private fun starts(events: List<String>): Int = events.count { it == "start" }

    private fun ring(
        mode: RingerMode,
        ringtone: RingtonePlayer,
        vibrator: CallVibrator
    ): SautiIncomingRing = SautiIncomingRing(ringtone, vibrator, FixedRingerModeProvider(mode))

    @Test
    fun silentStartsNoSoundAndNoVibrate() {
        val ringtone = RecordingRingtonePlayer()
        val vibrator = RecordingCallVibrator()

        ring(RingerMode.SILENT, ringtone, vibrator).start()

        assertEquals(0, starts(ringtone.events))
        assertEquals(0, starts(vibrator.events))
    }

    @Test
    fun vibrateStartsVibratorOnly() {
        val ringtone = RecordingRingtonePlayer()
        val vibrator = RecordingCallVibrator()

        ring(RingerMode.VIBRATE, ringtone, vibrator).start()

        assertEquals(0, starts(ringtone.events))
        assertEquals(1, starts(vibrator.events))
    }

    @Test
    fun normalStartsSoundAndVibrator() {
        val ringtone = RecordingRingtonePlayer()
        val vibrator = RecordingCallVibrator()

        ring(RingerMode.NORMAL, ringtone, vibrator).start()

        assertEquals(1, starts(ringtone.events))
        assertEquals(1, starts(vibrator.events))
    }

    @Test
    fun stopStopsRingtoneAndCancelsVibrator() {
        val ringtone = RecordingRingtonePlayer()
        val vibrator = RecordingCallVibrator()
        val incoming = ring(RingerMode.NORMAL, ringtone, vibrator)

        incoming.start()
        incoming.stop()

        assertEquals("stop", vibrator.events.last())
        assertEquals(1, starts(vibrator.events))
        assertTrue(ringtone.events.contains("stop"))
    }

    @Test
    fun doubleStartIsIdempotent() {
        val ringtone = RecordingRingtonePlayer()
        val vibrator = RecordingCallVibrator()
        val incoming = ring(RingerMode.NORMAL, ringtone, vibrator)

        incoming.start()
        incoming.start()

        assertEquals(1, starts(ringtone.events))
        assertEquals(1, starts(vibrator.events))
    }

    @Test
    fun stopBeforeStartIsSafeNoOp() {
        val ringtone = RecordingRingtonePlayer()
        val vibrator = RecordingCallVibrator()
        val incoming = ring(RingerMode.NORMAL, ringtone, vibrator)

        incoming.stop()

        assertEquals(0, starts(ringtone.events))
        assertEquals(0, starts(vibrator.events))
        assertEquals(emptyList(), ringtone.events)
        assertEquals(emptyList(), vibrator.events)
    }
}
