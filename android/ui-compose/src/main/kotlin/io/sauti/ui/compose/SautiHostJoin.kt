package io.sauti.ui.compose

import android.content.Context
import io.sauti.android.SautiClient
import io.sauti.android.SautiJoinRequest
import io.sauti.android.net.ConnectivityPolicy
import io.sauti.android.service.CallForegroundService
import io.sauti.android.service.CallNotification
import io.sauti.android.service.SautiCallIntents
import io.sauti.engine.EngineConfig

internal object SautiHostJoin {

    fun join(context: Context, ticket: SautiSessionTicket, defaults: SautiSessionDefaults) {
        val appContext = context.applicationContext
        val client = SautiClient(
            context = appContext,
            engineConfig = EngineConfig(onQoe = defaults.onQoe),
            connectivityPolicy = ConnectivityPolicy(debounceMs = defaults.iceRestartDebounceMs),
            enableProximity = defaults.enableProximity,
            audioProcessing = defaults.audioProcessing
        )
        CallForegroundService.startCall(
            context = appContext,
            client = client,
            request = SautiJoinRequest(
                url = ticket.url,
                token = ticket.token,
                roomId = ticket.roomId,
                participantId = ticket.participantId,
                slotGeneration = ticket.slotGeneration,
                displayTitle = ticket.displayTitle,
                endWhenLastPeerLeaves = ticket.endWhenLastPeerLeaves
            ),
            intents = SautiCallIntents(
                contentIntent = SautiCallActivity.resumePendingIntent(appContext),
                hangupIntent = CallNotification.hangupIntent(appContext)
            ),
            initialDevice = ticket.initialDevice
        )
    }
}
