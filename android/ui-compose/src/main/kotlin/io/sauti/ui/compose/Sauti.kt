package io.sauti.ui.compose

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.sauti.android.incoming.DefaultForegroundProbe
import io.sauti.android.incoming.ForegroundProbe
import io.sauti.android.incoming.IncomingCallNotification
import io.sauti.android.incoming.IncomingCallOverrides
import io.sauti.android.incoming.SautiIncomingCall
import io.sauti.android.incoming.SautiIncomingCallPresenter
import io.sauti.android.incoming.SautiIncomingCallRegistry
import io.sauti.ui.compose.outgoing.SautiOutgoingController

object Sauti {

    fun configure(config: SautiCallHost.Config) {
        SautiCallHost.configure(config)
    }

    fun startCall(context: Context, target: String, metadata: Map<String, String> = emptyMap()) =
        SautiOutgoingController.start(context, target, metadata)

    fun outgoingDeclined(context: Context, callId: String) =
        SautiOutgoingController.onDeclined(context, callId)

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
