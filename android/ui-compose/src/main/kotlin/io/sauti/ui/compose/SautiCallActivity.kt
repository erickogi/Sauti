package io.sauti.ui.compose

import android.Manifest
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.lifecycle.lifecycleScope
import io.sauti.android.SautiCall
import io.sauti.android.incoming.IncomingCallNotification
import io.sauti.android.incoming.SautiIncomingCall
import io.sauti.android.incoming.SautiIncomingCallRegistry
import io.sauti.android.outgoing.SautiOutgoingCallRegistry
import io.sauti.android.overlay.DefaultOverlayPermission
import io.sauti.android.overlay.manageOverlayIntent
import io.sauti.ui.compose.overlay.OverlayPromptDecision
import io.sauti.ui.compose.overlay.SautiBubbleOptIn
import io.sauti.android.ring.SautiIncomingRing
import io.sauti.android.service.CallForegroundService
import io.sauti.engine.CallPhase
import io.sauti.ui.compose.incoming.IncomingBusyDecision
import io.sauti.ui.compose.incoming.SautiIncomingCallScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SautiCallActivity : ComponentActivity() {

    private lateinit var config: SautiCallHost.Config

    private var incomingCall: SautiIncomingCall? = null
    private var callId: String? = null
    private var finisher: SautiIncomingCallRegistry.Finisher? = null
    private var ring: SautiIncomingRing? = null
    private var joinTimeoutJob: Job? = null
    private var missedCallJob: Job? = null
    private var renderedPhase: SautiHostPhase = SautiHostPhase.INCOMING
    private var outgoing: Boolean = false
    private var outgoingTitle: String = ""
    private var outgoingFinisher: SautiOutgoingCallRegistry.Finisher? = null
    private var overlayPromptDismissed: Boolean = false
    private val showOverlayPrompt = mutableStateOf(false)

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            moveTaskToBack(true)
        }

    private val accepted = mutableStateOf(false)
    private val sawSession = mutableStateOf(false)
    private val aborted = mutableStateOf(false)

    private val acceptPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            onPermissionResult(result)
        }

    private val cellularStatePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) CallForegroundService.rearmTelephony(applicationContext)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = SautiCallHost.require()
        applyWakeFlags()
        accepted.value = savedInstanceState?.getBoolean(STATE_ACCEPTED) ?: false
        sawSession.value = savedInstanceState?.getBoolean(STATE_SAW_SESSION) ?: false
        aborted.value = savedInstanceState?.getBoolean(STATE_ABORTED) ?: false
        overlayPromptDismissed = savedInstanceState?.getBoolean(STATE_OVERLAY_DISMISSED) ?: false
        showOverlayPrompt.value = savedInstanceState?.getBoolean(STATE_OVERLAY_PROMPT) ?: false
        val incoming = SautiIncomingCall.fromExtras(intent.extras)
        when {
            incoming != null -> setupIncoming(incoming)
            isOutgoing(intent) -> setupOutgoing(intent)
            isResume(intent) || CallForegroundService.call.value != null -> setupResume()
            else -> {
                finishAndRemoveTask()
                return
            }
        }
        if (!outgoing && accepted.value && !sawSession.value && CallForegroundService.call.value == null && !aborted.value) {
            startJoinTimeout()
        }
        registerBackHandler()
        setContent { HostRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val incoming = SautiIncomingCall.fromExtras(intent.extras)
        when {
            incoming != null && incoming.callId != callId -> {
                val busy = IncomingBusyDecision.declineBusy(
                    accepted = accepted.value,
                    outgoing = outgoing,
                    hasActiveSession = CallForegroundService.call.value != null,
                    newCallId = incoming.callId,
                    currentCallId = callId
                )
                if (busy) {
                    autoDeclineBusy(incoming)
                } else {
                    setIntent(intent)
                    rebindIncoming(incoming)
                }
            }
            isResume(intent) && CallForegroundService.call.value != null -> {
                setIntent(intent)
                cancelMissedCallTimeout()
                stopRing()
                accepted.value = true
            }
        }
    }

    private fun autoDeclineBusy(incoming: SautiIncomingCall) {
        IncomingCallNotification.cancel(this, incoming.callId)
        SautiIncomingCallRegistry.release(incoming.callId)
        val handler = config.onBusyDecline ?: config.onDecline
        SautiCallHost.scope.launch { runCatching { handler(incoming) } }
    }

    private fun setupIncoming(incoming: SautiIncomingCall) {
        incomingCall = incoming
        callId = incoming.callId
        IncomingCallNotification.cancel(this, incoming.callId)
        val registered = SautiIncomingCallRegistry.Finisher { runOnUiThread { onRemoteFinish() } }
        finisher = registered
        SautiIncomingCallRegistry.register(incoming.callId, registered)
        if (!accepted.value) {
            startRing()
            startMissedCallTimeout()
        }
    }

    private fun rebindIncoming(incoming: SautiIncomingCall) {
        releaseRegistration()
        joinTimeoutJob?.cancel()
        joinTimeoutJob = null
        renderedPhase = SautiHostPhase.INCOMING
        incomingCall = incoming
        callId = incoming.callId
        outgoing = false
        accepted.value = false
        sawSession.value = false
        aborted.value = false
        overlayPromptDismissed = false
        showOverlayPrompt.value = false
        val registered = SautiIncomingCallRegistry.Finisher { runOnUiThread { onRemoteFinish() } }
        finisher = registered
        SautiIncomingCallRegistry.register(incoming.callId, registered)
        IncomingCallNotification.cancel(this, incoming.callId)
        startRing()
        startMissedCallTimeout()
    }

    private fun setupResume() {
        accepted.value = true
    }

    private fun setupOutgoing(intent: Intent) {
        outgoing = true
        outgoingTitle = intent.getStringExtra(EXTRA_OUTGOING_TITLE).orEmpty()
        accepted.value = true
        val registered = SautiOutgoingCallRegistry.Finisher { runOnUiThread { onOutgoingHostFinish() } }
        outgoingFinisher = registered
        SautiOutgoingCallRegistry.registerFinisher(registered)
    }

    private fun onOutgoingHostFinish() {
        if (isFinishing) return
        finishAndRemoveTask()
    }

    @Composable
    private fun HostRoot() {
        val session by CallForegroundService.call.collectAsState()
        val endedReason by CallForegroundService.endedReason.collectAsState()
        val connected by produceState(false, session) {
            value = false
            val active = session
            if (active != null) active.state.collect { value = it.phase == CallPhase.CONNECTED }
        }
        LaunchedEffect(session) {
            if (session != null) {
                sawSession.value = true
                joinTimeoutJob?.cancel()
                joinTimeoutJob = null
                cancelMissedCallTimeout()
            }
        }
        val phase = if (aborted.value) {
            SautiHostPhase.ENDED
        } else {
            resolveHostPhase(
                accepted = accepted.value,
                sawSession = sawSession.value,
                hasSession = session != null,
                connected = connected,
                ended = endedReason != null
            )
        }
        SideEffect { renderedPhase = phase }
        LaunchedEffect(phase) {
            if (phase == SautiHostPhase.ENDED) {
                stopRing()
                delay(ENDED_LINGER_MS)
                if (!isFinishing) finishAndRemoveTask()
            }
        }
        SautiTheme(colors = config.colors, typography = config.typography, strings = config.strings) {
            when (resolveHostScreen(phase, session != null, incomingCall != null)) {
                SautiHostScreen.INCOMING -> IncomingContent()
                SautiHostScreen.CONNECTING -> SautiConnectingScreen(callerName = callerName())
                SautiHostScreen.SOLO -> InCallContent(session)
                SautiHostScreen.ENDED -> SautiConnectingScreen(callerName = callerName())
            }
            if (showOverlayPrompt.value) {
                val custom = config.overlayPromptContent
                if (custom != null) {
                    custom({ onOverlayPromptConfirm() }, { onOverlayPromptDismiss() })
                } else {
                    OverlayPermissionDialog(
                        strings = config.strings,
                        onConfirm = { onOverlayPromptConfirm() },
                        onDismiss = { onOverlayPromptDismiss() }
                    )
                }
            }
        }
    }

    @Composable
    private fun OverlayPermissionDialog(
        strings: SautiStrings,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(strings.overlayPromptTitle) },
            text = { Text(strings.overlayPromptBody) },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(strings.overlayPromptConfirm) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(strings.overlayPromptDismiss) }
            }
        )
    }

    @Composable
    private fun IncomingContent() {
        val incoming = incomingCall ?: return
        SautiIncomingCallScreen(
            callerName = config.callerNameResolver(incoming).ifBlank { hostAppCallerLabel(this@SautiCallActivity) },
            colors = config.colors,
            typography = config.typography,
            strings = config.strings,
            requiresSlide = config.incomingRequiresSlide,
            onAccept = { onAcceptClicked() },
            onDecline = { onDeclineClicked() }
        )
    }

    @Composable
    private fun InCallContent(session: SautiCall?) {
        val active = session ?: return
        val uiState = rememberSautiCallUiState(active, active.selfParticipantId)
        SautiSoloCallScreen(
            uiState = uiState,
            onEnd = { onEndClicked() },
            onMinimize = { minimize() }
        )
    }

    private fun callerName(): String {
        val incoming = incomingCall
        return when {
            incoming != null -> config.callerNameResolver(incoming).ifBlank { hostAppCallerLabel(this@SautiCallActivity) }
            outgoing -> outgoingTitle
            else -> ""
        }
    }

    private fun onAcceptClicked() {
        val incoming = incomingCall ?: return
        cancelMissedCallTimeout()
        if (permissionsGranted()) {
            beginAccept(incoming)
        } else {
            acceptPermissionLauncher.launch(config.acceptPermissions.toTypedArray())
        }
    }

    private fun onPermissionResult(result: Map<String, Boolean>) {
        val incoming = incomingCall ?: return
        val granted = config.acceptPermissions.all { result[it] == true }
        if (granted) beginAccept(incoming) else config.onAcceptPermissionDenied()
    }

    private fun permissionsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return config.acceptPermissions.all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun beginAccept(incoming: SautiIncomingCall) {
        if (accepted.value) return
        accepted.value = true
        cancelMissedCallTimeout()
        stopRing()
        startJoinTimeout()
        requestCellularStateIfEnabled()
        val onAccept = config.onAccept
        val provider = config.tokenProvider
        val onAfterAccept = config.onAfterAccept
        val defaults = config.sessionDefaults
        val autoMute = config.autoMuteOnCellularCall
        val appContext = applicationContext
        SautiCallHost.scope.launch {
            val ticket = runCatching {
                resolveTicket(provider, SautiTokenRequest.Accept(incoming)) { onAccept(incoming) }
            }.getOrNull() ?: return@launch
            runCatching {
                SautiHostJoin.join(appContext, ticket, defaults, autoMute)
                onAfterAccept(incoming)
            }
        }
    }

    private fun requestCellularStateIfEnabled() {
        if (!config.autoMuteOnCellularCall) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) return
        runCatching { cellularStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) }
    }

    private fun startJoinTimeout() {
        joinTimeoutJob?.cancel()
        joinTimeoutJob = lifecycleScope.launch {
            delay(JOIN_TIMEOUT_MS)
            if (!sawSession.value && CallForegroundService.call.value == null) driveAbort()
        }
    }

    private fun driveAbort() {
        joinTimeoutJob?.cancel()
        joinTimeoutJob = null
        aborted.value = true
    }

    private fun startMissedCallTimeout() {
        missedCallJob?.cancel()
        missedCallJob = lifecycleScope.launch {
            delay(config.incomingRingTimeoutMs)
            onMissedCall()
        }
    }

    private fun cancelMissedCallTimeout() {
        missedCallJob?.cancel()
        missedCallJob = null
    }

    private fun onMissedCall() {
        cancelMissedCallTimeout()
        stopRing()
        val incoming = incomingCall
        releaseRegistration()
        if (incoming != null) {
            val onMissed = config.onMissedCall
            SautiCallHost.scope.launch { runCatching { onMissed(incoming) } }
        }
        if (!isFinishing) finishAndRemoveTask()
    }

    private fun onDeclineClicked() {
        val incoming = incomingCall
        cancelMissedCallTimeout()
        stopRing()
        if (incoming != null) {
            val onDecline = config.onDecline
            SautiCallHost.scope.launch { runCatching { onDecline(incoming) } }
        }
        finishAndRemoveTask()
    }

    private fun onEndClicked() {
        val id = callId
        if (id != null) {
            val onEndCall = config.onEndCall
            SautiCallHost.scope.launch { runCatching { onEndCall(id) } }
        }
        CallForegroundService.stop(this)
    }

    private fun onRemoteFinish() {
        if (accepted.value) return
        if (isFinishing) return
        cancelMissedCallTimeout()
        stopRing()
        finishAndRemoveTask()
    }

    private fun minimize() {
        val shouldPrompt = OverlayPromptDecision.shouldPrompt(
            optedIn = SautiBubbleOptIn.enabled,
            granted = DefaultOverlayPermission(this).granted(),
            dismissed = overlayPromptDismissed
        )
        if (shouldPrompt) {
            showOverlayPrompt.value = true
            return
        }
        moveTaskToBack(true)
    }

    private fun onOverlayPromptConfirm() {
        showOverlayPrompt.value = false
        runCatching { overlayPermissionLauncher.launch(manageOverlayIntent(this)) }
            .onFailure { moveTaskToBack(true) }
    }

    private fun onOverlayPromptDismiss() {
        showOverlayPrompt.value = false
        overlayPromptDismissed = true
        moveTaskToBack(true)
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showOverlayPrompt.value) {
                    onOverlayPromptDismiss()
                    return
                }
                when (renderedPhase) {
                    SautiHostPhase.INCOMING -> onDeclineClicked()
                    SautiHostPhase.CONNECTING -> minimize()
                    SautiHostPhase.IN_CALL -> minimize()
                    SautiHostPhase.ENDED -> finishAndRemoveTask()
                }
            }
        })
    }

    private fun startRing() {
        if (ring != null) return
        val created = SautiIncomingRing.create(applicationContext, config.ringOverrides)
        ring = created
        created.start()
    }

    private fun stopRing() {
        ring?.stop()
        ring = null
    }

    private fun releaseRegistration() {
        val id = callId ?: return
        finisher?.let { SautiIncomingCallRegistry.unregister(id, it) }
        SautiIncomingCallRegistry.release(id)
        IncomingCallNotification.cancel(this, id)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ACCEPTED, accepted.value)
        outState.putBoolean(STATE_SAW_SESSION, sawSession.value)
        outState.putBoolean(STATE_ABORTED, aborted.value)
        outState.putBoolean(STATE_OVERLAY_DISMISSED, overlayPromptDismissed)
        outState.putBoolean(STATE_OVERLAY_PROMPT, showOverlayPrompt.value)
    }

    override fun onStop() {
        stopRing()
        super.onStop()
    }

    override fun onDestroy() {
        stopRing()
        joinTimeoutJob?.cancel()
        joinTimeoutJob = null
        cancelMissedCallTimeout()
        if (!isChangingConfigurations) releaseRegistration()
        outgoingFinisher?.let { SautiOutgoingCallRegistry.unregisterFinisher(it) }
        outgoingFinisher = null
        super.onDestroy()
    }

    private fun isResume(intent: Intent): Boolean = intent.getBooleanExtra(EXTRA_RESUME, false)

    private fun isOutgoing(intent: Intent): Boolean = intent.getBooleanExtra(EXTRA_OUTGOING, false)

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

    companion object {
        const val EXTRA_RESUME = "io.sauti.ui.compose.EXTRA_RESUME"
        const val EXTRA_OUTGOING = "io.sauti.ui.compose.EXTRA_OUTGOING"
        const val EXTRA_OUTGOING_TITLE = "io.sauti.ui.compose.EXTRA_OUTGOING_TITLE"

        private const val JOIN_TIMEOUT_MS = 20_000L
        private const val ENDED_LINGER_MS = 1_200L
        private const val STATE_ACCEPTED = "io.sauti.ui.compose.STATE_ACCEPTED"
        private const val STATE_SAW_SESSION = "io.sauti.ui.compose.STATE_SAW_SESSION"
        private const val STATE_ABORTED = "io.sauti.ui.compose.STATE_ABORTED"
        private const val STATE_OVERLAY_DISMISSED = "io.sauti.ui.compose.STATE_OVERLAY_DISMISSED"
        private const val STATE_OVERLAY_PROMPT = "io.sauti.ui.compose.STATE_OVERLAY_PROMPT"
        private const val RESUME_REQUEST = 0x5A08

        fun resumeIntent(context: Context): Intent =
            Intent(context, SautiCallActivity::class.java).apply {
                putExtra(EXTRA_RESUME, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }

        fun outgoingIntent(context: Context, title: String): Intent =
            Intent(context, SautiCallActivity::class.java).apply {
                putExtra(EXTRA_OUTGOING, true)
                putExtra(EXTRA_OUTGOING_TITLE, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }

        internal fun resumePendingIntent(context: Context): PendingIntent {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getActivity(context, RESUME_REQUEST, resumeIntent(context), flags)
        }
    }
}
