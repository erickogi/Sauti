package io.sauti.android.ring

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioManagerRingerModeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun audioManager(): AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Test
    fun silentMapsToSilent() {
        audioManager().ringerMode = AudioManager.RINGER_MODE_SILENT
        assertEquals(RingerMode.SILENT, AudioManagerRingerMode(context).current())
    }

    @Test
    fun vibrateMapsToVibrate() {
        audioManager().ringerMode = AudioManager.RINGER_MODE_VIBRATE
        assertEquals(RingerMode.VIBRATE, AudioManagerRingerMode(context).current())
    }

    @Test
    fun normalMapsToNormal() {
        audioManager().ringerMode = AudioManager.RINGER_MODE_NORMAL
        assertEquals(RingerMode.NORMAL, AudioManagerRingerMode(context).current())
    }

    @Test
    @Config(sdk = [28])
    fun mappingHoldsOnLegacySdk() {
        audioManager().ringerMode = AudioManager.RINGER_MODE_VIBRATE
        assertEquals(RingerMode.VIBRATE, AudioManagerRingerMode(context).current())
    }
}
