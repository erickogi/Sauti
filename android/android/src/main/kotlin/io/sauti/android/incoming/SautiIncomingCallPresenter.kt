package io.sauti.android.incoming

import android.content.ComponentName
import android.content.Context
import android.content.Intent

class SautiIncomingCallPresenter(
    private val context: Context,
    private val target: ComponentName,
    private val foreground: ForegroundProbe = DefaultForegroundProbe(context),
    private val registry: SautiIncomingCallRegistry = SautiIncomingCallRegistry,
    private val overrides: IncomingCallOverrides = IncomingCallOverrides(),
    private val actions: CallActionIntents? = null,
    private val activityIntentFlags: Int = Intent.FLAG_ACTIVITY_NEW_TASK
) {

    fun present(call: SautiIncomingCall) {
        if (call.callId.isBlank()) return
        if (!registry.shouldPresent(call.callId)) return
        if (foreground.isForeground()) {
            try {
                launchActivity(call)
            } catch (error: android.content.ActivityNotFoundException) {
                postNotification(call)
            } catch (error: SecurityException) {
                postNotification(call)
            }
        } else {
            postNotification(call)
        }
    }

    fun cancel(callId: String) {
        IncomingCallNotification.cancel(context, callId)
        registry.finish(callId)
        registry.release(callId)
    }

    private fun launchActivity(call: SautiIncomingCall) {
        val intent = call.toExtras(Intent().setComponent(target)).addFlags(activityIntentFlags)
        context.startActivity(intent)
    }

    private fun postNotification(call: SautiIncomingCall) {
        IncomingCallNotification.post(context, call, target, overrides, actions)
    }
}
