package io.sauti.ui.compose

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.sauti.android.incoming.DefaultForegroundProbe
import io.sauti.android.incoming.ForegroundProbe
import io.sauti.android.incoming.IncomingCallNotification
import io.sauti.android.incoming.SautiIncomingCall
import io.sauti.android.incoming.SautiIncomingCallPresenter
import io.sauti.android.incoming.SautiIncomingCallRegistry

object Sauti {

    fun configure(config: SautiCallHost.Config) {
        SautiCallHost.configure(config)
    }

    fun presentIncomingCall(context: Context, call: SautiIncomingCall) {
        presenter(context, DefaultForegroundProbe(context)).present(call)
    }

    fun cancelIncomingCall(context: Context, callId: String) {
        SautiIncomingCallRegistry.cancel(callId)
        IncomingCallNotification.cancel(context, callId)
    }

    fun resumeIntent(context: Context): Intent = SautiCallActivity.resumeIntent(context)

    internal fun presenter(context: Context, foreground: ForegroundProbe): SautiIncomingCallPresenter =
        SautiIncomingCallPresenter(
            context = context,
            target = ComponentName(context, SautiCallActivity::class.java),
            foreground = foreground
        )
}
