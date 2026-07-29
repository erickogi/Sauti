package io.sauti.android.proximity

import io.sauti.android.audio.AudioDevice
import kotlin.test.Test
import kotlin.test.assertEquals

class ProximityReducerTest {

    private val allDevices = listOf(
        AudioDevice.EARPIECE,
        AudioDevice.SPEAKER,
        AudioDevice.BLUETOOTH,
        AudioDevice.WIRED_HEADSET
    )

    @Test
    fun offOnlyForActiveNearEarpiece() {
        for (device in allDevices) {
            for (active in listOf(true, false)) {
                for (near in listOf(true, false)) {
                    val expected =
                        if (active && near && device == AudioDevice.EARPIECE) {
                            ScreenIntent.Off
                        } else {
                            ScreenIntent.On
                        }
                    assertEquals(
                        expected,
                        ProximityReducer.reduce(active, near, device),
                        "active=$active near=$near device=$device"
                    )
                }
            }
        }
    }

    @Test
    fun activeNearEarpieceBlanks() {
        assertEquals(
            ScreenIntent.Off,
            ProximityReducer.reduce(true, true, AudioDevice.EARPIECE)
        )
    }

    @Test
    fun awayRestoresWhileActiveOnEarpiece() {
        assertEquals(
            ScreenIntent.On,
            ProximityReducer.reduce(true, false, AudioDevice.EARPIECE)
        )
    }

    @Test
    fun speakerNeverBlanksEvenWhenNearAndActive() {
        assertEquals(
            ScreenIntent.On,
            ProximityReducer.reduce(true, true, AudioDevice.SPEAKER)
        )
    }

    @Test
    fun bluetoothNeverBlanksEvenWhenNearAndActive() {
        assertEquals(
            ScreenIntent.On,
            ProximityReducer.reduce(true, true, AudioDevice.BLUETOOTH)
        )
    }

    @Test
    fun wiredHeadsetNeverBlanksEvenWhenNearAndActive() {
        assertEquals(
            ScreenIntent.On,
            ProximityReducer.reduce(true, true, AudioDevice.WIRED_HEADSET)
        )
    }

    @Test
    fun midCallRouteSwitchEarpieceToSpeakerWhileNearRestores() {
        assertEquals(
            ScreenIntent.Off,
            ProximityReducer.reduce(true, true, AudioDevice.EARPIECE)
        )
        assertEquals(
            ScreenIntent.On,
            ProximityReducer.reduce(true, true, AudioDevice.SPEAKER)
        )
    }

    @Test
    fun callEndRestoresWhileStillNearOnEarpiece() {
        assertEquals(
            ScreenIntent.Off,
            ProximityReducer.reduce(true, true, AudioDevice.EARPIECE)
        )
        assertEquals(
            ScreenIntent.On,
            ProximityReducer.reduce(false, true, AudioDevice.EARPIECE)
        )
    }

    @Test
    fun idleAlwaysRestores() {
        for (device in allDevices) {
            for (near in listOf(true, false)) {
                assertEquals(
                    ScreenIntent.On,
                    ProximityReducer.reduce(false, near, device),
                    "near=$near device=$device"
                )
            }
        }
    }
}
