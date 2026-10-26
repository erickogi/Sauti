package io.sauti.android.telecom

import io.sauti.engine.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiConnectionTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val state = MutableStateFlow(CallState())

    @Test
    fun blankDisplayTitleFallsBackToNonBlankCallerName() {
        val connection = SautiConnection(scope, state, "", "participant-1") {}
        assertTrue(!connection.callerDisplayName.isNullOrBlank())
    }

    @Test
    fun nullDisplayTitleFallsBackToNonBlankCallerName() {
        val connection = SautiConnection(scope, state, null, "participant-1") {}
        assertTrue(!connection.callerDisplayName.isNullOrBlank())
    }

    @Test
    fun presentDisplayTitleIsPreserved() {
        val connection = SautiConnection(scope, state, "Ada", "participant-1") {}
        assertTrue(connection.callerDisplayName == "Ada")
    }
}
