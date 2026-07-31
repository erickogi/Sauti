package io.sauti.android.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import io.sauti.android.SautiCall
import io.sauti.android.SautiClient
import io.sauti.android.SautiJoinRequest
import io.sauti.android.audio.AudioDevice
import io.sauti.engine.CallPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class CallForegroundService : Service() {

    private var owningClient: SautiClient? = null
    private var storedIntents: SautiCallIntents? = null
    private var serviceScope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            intent == null -> stopSelfCompat()
            intent.action == ACTION_STOP -> endCall()
            intent.action == ACTION_ADOPT -> adopt()
            else -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: DEFAULT_TITLE
                val presence = presenceOf(intent.getStringExtra(EXTRA_PRESENCE))
                goForeground(title, presence)
            }
        }
        return START_NOT_STICKY
    }

    private fun adopt() {
        val adoption = pendingAdoption.getAndSet(null) ?: return
        serviceScope?.cancel()
        serviceScope = null
        val previous = owningClient
        if (previous != null && previous !== adoption.client) {
            previous.leaveInternal()
            previous.dispose()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        owningClient = adoption.client
        storedIntents = adoption.intents
        serviceScope = scope
        currentCall.value = adoption.client
        goForeground(adoption.request.displayTitle, CallPresence.CONNECTING)
        scope.launch {
            adoption.client.join(adoption.request)
            adoption.initialDevice?.let { adoption.client.selectDevice(it) }
        }
        scope.launch {
            adoption.client.state.collect { state ->
                if (state.phase == CallPhase.LEFT) endCall()
            }
        }
    }

    private fun endCall() {
        val client = owningClient
        if (client == null) {
            stopSelfCompat()
            return
        }
        owningClient = null
        storedIntents = null
        currentCall.value = null
        serviceScope?.cancel()
        serviceScope = null
        client.leaveInternal()
        client.dispose()
        stopSelfCompat()
    }

    private fun goForeground(title: String, presence: CallPresence) {
        val intents = storedIntents
        val notification = CallNotification.build(
            context = this,
            title = title,
            presence = presence,
            fullScreenIntent = intents?.contentIntent ?: CallNotification.placeholderIntent(this),
            contentIntent = intents?.contentIntent,
            hangupIntent = intents?.hangupIntent ?: CallNotification.hangupIntent(this)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                CallNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(CallNotification.NOTIFICATION_ID, notification)
        }
    }

    private fun stopSelfCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun presenceOf(raw: String?): CallPresence = when (raw) {
        CallPresence.RECONNECTING.name -> CallPresence.RECONNECTING
        CallPresence.ONGOING.name -> CallPresence.ONGOING
        else -> CallPresence.CONNECTING
    }

    companion object {
        const val ACTION_STOP = "io.sauti.android.action.STOP"
        const val ACTION_ADOPT = "io.sauti.android.action.ADOPT"
        const val EXTRA_TITLE = "io.sauti.android.extra.TITLE"
        const val EXTRA_PRESENCE = "io.sauti.android.extra.PRESENCE"
        private const val DEFAULT_TITLE = "Call"

        private val currentCall = MutableStateFlow<SautiCall?>(null)
        val call: StateFlow<SautiCall?> get() = currentCall.asStateFlow()

        private val pendingAdoption = AtomicReference<Adoption?>(null)

        private data class Adoption(
            val client: SautiClient,
            val request: SautiJoinRequest,
            val intents: SautiCallIntents,
            val initialDevice: AudioDevice?
        )

        fun startCall(
            context: Context,
            client: SautiClient,
            request: SautiJoinRequest,
            intents: SautiCallIntents,
            initialDevice: AudioDevice? = null
        ) {
            pendingAdoption.getAndSet(Adoption(client, request, intents, initialDevice))?.client?.dispose()
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_ADOPT
                putExtra(EXTRA_TITLE, request.displayTitle)
                putExtra(EXTRA_PRESENCE, CallPresence.CONNECTING.name)
            }
            startServiceCompat(context, intent)
        }

        fun start(context: Context, title: String, presence: CallPresence) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PRESENCE, presence.name)
            }
            startServiceCompat(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
