package io.sauti.ui.compose

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.sauti.android.incoming.DefaultForegroundProbe
import io.sauti.android.incoming.ForegroundProbe
import io.sauti.android.incoming.IncomingCallNotification
import io.sauti.android.incoming.IncomingCallOverrides
import io.sauti.android.incoming.SautiIncomingCall
import io.sauti.android.incoming.SautiIncomingCallPresenter
import io.sauti.android.incoming.SautiIncomingCallRegistry
import io.sauti.android.service.CallForegroundService
import io.sauti.ui.compose.outgoing.SautiOutgoingController
import io.sauti.ui.compose.push.SautiPushCommand
import io.sauti.ui.compose.push.SautiPushKeys
import io.sauti.ui.compose.push.SautiPushParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

object Sauti {

    fun configure(config: SautiCallHost.Config) {
        SautiCallHost.configure(config)
    }

    fun startCall(context: Context, target: String, metadata: Map<String, String> = emptyMap()) =
        SautiOutgoingController.start(context, target, metadata)

    fun outgoingDeclined(context: Context, callId: String) =
        SautiOutgoingController.onDeclined(context, callId)

    fun handlePush(context: Context, data: Map<String, String>): Boolean =
        handlePush(context, data, SautiCallHost.optional()?.pushKeys ?: SautiPushKeys())

    fun handlePush(context: Context, data: Map<String, String>, keys: SautiPushKeys): Boolean {
        val command = SautiPushParser.parse(data, keys) ?: return false
        when (command) {
            is SautiPushCommand.Incoming -> presentIncomingCall(context, command.call)
            is SautiPushCommand.Cancelled -> cancelIncomingCall(context, command.callId)
            is SautiPushCommand.Declined -> outgoingDeclined(context, command.callId)
        }
        return true
    }

    fun presentIncomingCall(context: Context, call: SautiIncomingCall) {
        val overrides = SautiCallHost.optional()?.notificationOverrides ?: IncomingCallOverrides()
        presenter(context, DefaultForegroundProbe(context), overrides)
            .present(call)
    }

    fun cancelIncomingCall(context: Context, callId: String) {
        SautiIncomingCallRegistry.cancel(callId)
        IncomingCallNotification.cancel(context, callId)
    }

    fun resumeIntent(context: Context): Intent = SautiCallActivity.resumeIntent(context)

    val callState: StateFlow<SautiCallSummary> by lazy {
        callSummaryFlow(CallForegroundService.call)
            .stateIn(SautiCallHost.scope, SharingStarted.Eagerly, SautiCallSummary.Inactive)
    }

    val isCallActive: Boolean get() = CallForegroundService.call.value != null

    fun missingCallPermissions(context: Context): List<String> {
        val base = SautiCallHost.optional()?.acceptPermissions ?: listOf(Manifest.permission.RECORD_AUDIO)
        val required = SautiCallPermissions.required(Build.VERSION.SDK_INT, base)
        val granted = required
            .filter { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
            .toSet()
        return SautiCallPermissions.missing(required, granted)
    }

    internal fun presenter(
        context: Context,
        foreground: ForegroundProbe,
        overrides: IncomingCallOverrides = IncomingCallOverrides()
    ): SautiIncomingCallPresenter =
        SautiIncomingCallPresenter(
            context = context,
            target = ComponentName(context, SautiCallActivity::class.java),
            foreground = foreground,
            overrides = overrides
        )
}
