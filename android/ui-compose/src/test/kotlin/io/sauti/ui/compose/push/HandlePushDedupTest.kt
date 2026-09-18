package io.sauti.ui.compose.push

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.incoming.SautiIncomingCallRegistry
import io.sauti.ui.compose.Sauti
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HandlePushDedupTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        SautiIncomingCallRegistry.finish("dedup-1")
        SautiIncomingCallRegistry.release("dedup-1")
        SautiIncomingCallRegistry.finish("tomb-1")
        SautiIncomingCallRegistry.release("tomb-1")
    }

    @Test
    fun handlePushIncomingRegistersWithDedupRegistry() {
        val data = mapOf(
            "event_name" to "voip-call-incoming",
            "payload" to """{"callId":"dedup-1","roomId":"r"}"""
        )
        assertTrue(Sauti.handlePush(context, data))
        assertFalse(SautiIncomingCallRegistry.shouldPresent("dedup-1"))
    }

    @Test
    fun handlePushCancelledTombstonesAgainstALaterRing() {
        val data = mapOf(
            "event_name" to "voip-call-cancelled",
            "payload" to """{"callId":"tomb-1"}"""
        )
        assertTrue(Sauti.handlePush(context, data))
        assertFalse(SautiIncomingCallRegistry.shouldPresent("tomb-1"))
    }
}
