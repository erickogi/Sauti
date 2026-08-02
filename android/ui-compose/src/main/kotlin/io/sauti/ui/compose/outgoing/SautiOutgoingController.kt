package io.sauti.ui.compose.outgoing

import android.content.Context
import io.sauti.android.outgoing.SautiOutgoingCallRegistry
import io.sauti.android.ring.CallRole
import io.sauti.android.ring.SautiRinger
import io.sauti.android.service.CallForegroundService
import io.sauti.ui.compose.SautiCallActivity
import io.sauti.ui.compose.SautiCallHost
import io.sauti.ui.compose.SautiHostJoin
import io.sauti.ui.compose.SautiOutgoingRequest
import io.sauti.ui.compose.SautiSessionTicket
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal object SautiOutgoingController {

    private class Live(
        val callId: String
    ) {
        var peerJoined: Boolean = false
        var tearingDown: Boolean = false
        var sawSession: Boolean = false
        var ringSelfId: String? = null
        var observerJob: Job? = null
        var ringer: SautiRinger? = null
    }

    private var live: Live? = null

    @Volatile
    private var watchdog: Job? = null

    @Volatile
    private var busy: Boolean = false

    @Volatile
    private var generation: Int = 0

    fun start(context: Context, target: String, metadata: Map<String, String> = emptyMap()) {
        val config = SautiCallHost.optional() ?: return
        val startGeneration: Int
        synchronized(this) {
            if (busy) return
            busy = true
            generation += 1
            startGeneration = generation
        }
        val app = context.applicationContext
        val title = metadata["name"]?.takeIf { it.isNotBlank() } ?: ""
        val launched = runCatching {
            context.startActivity(SautiCallActivity.outgoingIntent(context, title))
        }
        if (launched.isFailure) {
            reset()
            runCatching { config.onOutgoingFailed() }
            return
        }
        armWatchdog(app, config)
        SautiCallHost.scope.launch {
            val ticket = runCatching { config.onStartCall(SautiOutgoingRequest(target, metadata)) }.getOrNull()
            if (ticket == null) {
                failStart(app, config)
                return@launch
            }
            if (ticket.callId.isBlank()) {
                failStart(app, config)
                return@launch
            }
            if (!OutgoingStartGuard.shouldProceedAfterStart(startGeneration, generation)) {
                if (ticket.callId.isNotBlank()) {
                    runCatching { config.onCancelCall(ticket.callId) }
                }
                return@launch
            }
            val started = runCatching {
                SautiOutgoingCallRegistry.begin(ticket.callId)
                SautiHostJoin.join(app, ticket, config.sessionDefaults, config.autoMuteOnCellularCall)
                arm(app, ticket)
            }
            if (started.isFailure) failStart(app, config)
        }
    }

    fun onDeclined(context: Context, callId: String) {
        val app = context.applicationContext
        SautiCallHost.scope.launch {
            val record = live ?: return@launch
            if (record.callId != callId) return@launch
            if (!SautiOutgoingCallRegistry.matchesUnconnected(callId)) return@launch
            record.tearingDown = true
            SautiOutgoingCallRegistry.finishHost()
            CallForegroundService.stop(app)
            reset()
        }
    }

    private fun armWatchdog(app: Context, config: SautiCallHost.Config) {
        watchdog = SautiCallHost.scope.launch {
            delay(config.outgoingNoAnswerTimeoutMs)
            onNoAnswer(app)
        }
    }

    private fun arm(app: Context, ticket: SautiSessionTicket) {
        val record = Live(ticket.callId)
        live = record
        val ringer = SautiRinger.create(app, SautiCallHost.scope)
        record.ringer = ringer
        record.observerJob = SautiCallHost.scope.launch {
            CallForegroundService.call.collectLatest { session ->
                if (session == null) {
                    if (record.sawSession) onSessionGone()
                    return@collectLatest
                }
                record.sawSession = true
                record.ringSelfId = session.state.value.selfId
                ringer.start(CallRole.OUTGOING, session.state, record.ringSelfId)
                session.state.collect { snapshot ->
                    val resolvedSelf = snapshot.selfId
                    if (resolvedSelf != null && resolvedSelf != record.ringSelfId) {
                        record.ringSelfId = resolvedSelf
                        ringer.start(CallRole.OUTGOING, session.state, resolvedSelf)
                    }
                    if (OutgoingPeerPresence.peerJoined(snapshot.phase, snapshot.participants)) {
                        onPeerJoined()
                    }
                }
            }
        }
    }

    private fun onPeerJoined() {
        val record = live ?: return
        if (record.peerJoined) return
        record.peerJoined = true
        SautiOutgoingCallRegistry.markConnected()
        watchdog?.cancel()
        watchdog = null
    }

    private fun onNoAnswer(app: Context) {
        val record = live
        if (record?.peerJoined == true) return
        record?.tearingDown = true
        SautiOutgoingCallRegistry.finishHost()
        CallForegroundService.stop(app)
        val callId = record?.callId
        val cancel = OutgoingCancelDecision.cancelOnNoAnswer(
            peerJoined = record?.peerJoined == true,
            callKnown = callId != null,
            unconnectedMatch = callId != null && SautiOutgoingCallRegistry.matchesUnconnected(callId)
        )
        reset()
        if (cancel && callId != null) {
            SautiCallHost.scope.launch {
                runCatching { SautiCallHost.optional()?.onCancelCall?.invoke(callId) }
            }
        }
    }

    private fun onSessionGone() {
        val record = live ?: return
        val cancel = OutgoingCancelDecision.cancelOnSessionGone(record.peerJoined, record.tearingDown)
        val callId = record.callId
        SautiOutgoingCallRegistry.finishHost()
        reset()
        if (cancel) {
            SautiCallHost.scope.launch {
                runCatching { SautiCallHost.optional()?.onCancelCall?.invoke(callId) }
            }
        }
    }

    private fun failStart(app: Context, config: SautiCallHost.Config) {
        CallForegroundService.stop(app)
        reset()
        SautiOutgoingCallRegistry.finishHost()
        runCatching { config.onOutgoingFailed() }
    }

    private fun reset() {
        watchdog?.cancel()
        watchdog = null
        val record = live
        record?.observerJob?.cancel()
        record?.ringer?.stop()
        live = null
        SautiOutgoingCallRegistry.clear()
        synchronized(this) {
            busy = false
            generation += 1
        }
    }
}
