package io.sauti.ui.compose.incoming

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.sauti.android.incoming.IncomingCallNotification
import io.sauti.android.incoming.SautiIncomingCall
import io.sauti.android.incoming.SautiIncomingCallRegistry
import io.sauti.android.ring.RingOverrides

class SautiIncomingCallActivity : ComponentActivity() {

    private var coordinator: IncomingCallCoordinator? = null
    private var finisher: SautiIncomingCallRegistry.Finisher? = null
    private var callId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyWakeFlags()
        val incoming = SautiIncomingCall.fromExtras(intent.extras)
        if (incoming == null) {
            finish()
            return
        }
        callId = incoming.callId
        val mainHandler = Handler(Looper.getMainLooper())
        val finisher = SautiIncomingCallRegistry.Finisher {
            mainHandler.post { if (!isFinishing) finish() }
        }
        this.finisher = finisher
        SautiIncomingCallRegistry.register(incoming.callId, finisher)
        val coordinator = IncomingCallCoordinator(
            callId = incoming.callId,
            notifications = { IncomingCallNotification.cancel(this, it) },
            dedupe = { SautiIncomingCallRegistry.release(it) },
            ring = SautiIncomingCallHost.ringFactory(this, incoming, RingOverrides())
        )
        this.coordinator = coordinator
        coordinator.onShown()
        val config = SautiIncomingCallHost.current()
        setContent {
            SautiIncomingCallScreen(
                callerName = displayName(incoming),
                colors = config.colors,
                typography = config.typography,
                strings = config.strings,
                onAccept = {
                    config.onAccept(incoming)
                    coordinator.onAccepted()
                    finish()
                },
                onDecline = {
                    config.onDecline(incoming)
                    finish()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val id = callId
        val registered = finisher
        if (id != null && registered != null) {
            SautiIncomingCallRegistry.unregister(id, registered)
        }
        coordinator?.onDismissed()
    }

    private fun applyWakeFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguard?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun displayName(call: SautiIncomingCall): String {
        val fromField = call.callerName?.takeIf { it.isNotBlank() }
        val fromMetadata = call.metadata["name"]?.takeIf { it.isNotBlank() }
        return fromField ?: fromMetadata ?: shortId(call.callId)
    }

    private fun shortId(callId: String): String =
        if (callId.length <= SHORT_ID_LENGTH) callId else callId.take(SHORT_ID_LENGTH)

    private companion object {
        private const val SHORT_ID_LENGTH = 8
    }
}
