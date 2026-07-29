package io.sauti.android.telecom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.CallEngine
import io.sauti.android.SautiClient
import io.sauti.android.audio.AudioSessionCoordinator
import io.sauti.android.net.ConnectivityPolicy
import io.sauti.android.net.ConnectivityWatcher
import io.sauti.android.persistence.ResumeStore
import io.sauti.android.telephony.TelephonyWatcher
import io.sauti.engine.CallEvent
import io.sauti.engine.CallState
import io.sauti.engine.JoinConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeCallEngine : CallEngine {
    private val stateFlow = MutableStateFlow(CallState())
    private val eventsFlow = MutableSharedFlow<CallEvent>(extraBufferCapacity = 16)
    override val state: StateFlow<CallState> get() = stateFlow.asStateFlow()
    override val events: SharedFlow<CallEvent> get() = eventsFlow.asSharedFlow()
    override suspend fun join(config: JoinConfig) = Unit
    override fun setMuted(muted: Boolean) = Unit
    override fun setHold(onHold: Boolean) = Unit
    override fun onNetworkChanged() = Unit
    override fun leave() = Unit
    override fun dispose() = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiTelecomClientTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun fieldOf(client: SautiClient, name: String): Any? {
        val field = SautiClient::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(client)
    }

    @Test
    fun telecomWiringOwnsAudioViaTelecomControllerAndDropsTelephonyWatcher() {
        val client = SautiTelecomClient.build(
            context = context,
            scope = scope,
            connectivityPolicy = ConnectivityPolicy(),
            enableProximity = false,
            audio = TelecomAudioController(context),
            engine = FakeCallEngine()
        )

        assertTrue(fieldOf(client, "audio") is TelecomAudioController)
        assertFalse(fieldOf(client, "telephony") is TelephonyWatcher)
    }

    @Test
    fun defaultWiringKeepsAudioSessionCoordinatorAndTelephonyWatcher() {
        val client = SautiClient(
            context = context,
            scope = scope,
            audio = AudioSessionCoordinator(context),
            engine = FakeCallEngine(),
            resumeStore = ResumeStore(context),
            telephonyFactory = { ctx, cb -> TelephonyWatcher(ctx, cb) },
            connectivityFactory = { ctx, cb -> ConnectivityWatcher(ctx, cb) }
        )

        assertTrue(fieldOf(client, "audio") is AudioSessionCoordinator)
        assertTrue(fieldOf(client, "telephony") is TelephonyWatcher)
    }
}
