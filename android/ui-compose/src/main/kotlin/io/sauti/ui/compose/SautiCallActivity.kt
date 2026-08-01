package io.sauti.ui.compose

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
import io.sauti.android.ring.SautiIncomingRing
import io.sauti.android.service.CallForegroundService
import io.sauti.engine.CallPhase
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
    private var renderedPhase: SautiHostPhase = SautiHostPhase.INCOMING

    private val accepted = mutableStateOf(false)
    private val sawSession = mutableStateOf(false)
    private val aborted = mutableStateOf(false)

    private val acceptPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            onPermissionResult(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = SautiCallHost.require()
        applyWakeFlags()
        accepted.value = savedInstanceState?.getBoolean(STATE_ACCEPTED) ?: false
        sawSession.value = savedInstanceState?.getBoolean(STATE_SAW_SESSION) ?: false
        aborted.value = savedInstanceState?.getBoolean(STATE_ABORTED) ?: false
        val incoming = SautiIncomingCall.fromExtras(intent.extras)
        when {
            incoming != null -> setupIncoming(incoming)
            isResume(intent) || CallForegroundService.call.value != null -> setupResume()
            else -> {
                finish()
                return
            }
        }
        if (accepted.value && !sawSession.value && CallForegroundService.call.value == null && !aborted.value) {
            startJoinTimeout()
        }
        registerBackHandler()
        setContent { HostRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incoming = SautiIncomingCall.fromExtras(intent.extras)
        when {
            incoming != null && incoming.callId != callId -> rebindIncoming(incoming)
            isResume(intent) && CallForegroundService.call.value != null -> {
                stopRing()
                accepted.value = true
            }
        }
    }

    private fun setupIncoming(incoming: SautiIncomingCall) {
        incomingCall = incoming
        callId = incoming.callId
        IncomingCallNotification.cancel(this, incoming.callId)
        val registered = SautiIncomingCallRegistry.Finisher { runOnUiThread { onRemoteFinish() } }
        finisher = registered
        SautiIncomingCallRegistry.register(incoming.callId, registered)
        if (!accepted.value) startRing()
    }

    private fun rebindIncoming(incoming: SautiIncomingCall) {
        releaseRegistration()
        joinTimeoutJob?.cancel()
        joinTimeoutJob = null
        renderedPhase = SautiHostPhase.INCOMING
        incomingCall = incoming
        callId = incoming.callId
        accepted.value = false
        sawSession.value = false
        aborted.value = false
        val registered = SautiIncomingCallRegistry.Finisher { runOnUiThread { onRemoteFinish() } }
        finisher = registered
        SautiIncomingCallRegistry.register(incoming.callId, registered)
        IncomingCallNotification.cancel(this, incoming.callId)
        startRing()
    }

    private fun setupResume() {
        accepted.value = true
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
                if (!isFinishing) finish()
            }
        }
        SautiTheme(colors = config.colors, typography = config.typography, strings = config.strings) {
            when (phase) {
                SautiHostPhase.INCOMING -> IncomingContent()
                SautiHostPhase.CONNECTING -> SautiConnectingScreen(callerName = callerName())
                SautiHostPhase.IN_CALL -> InCallContent(session)
                SautiHostPhase.ENDED -> SautiConnectingScreen(callerName = callerName())
            }
        }
    }

    @Composable
    private fun IncomingContent() {
        val incoming = incomingCall ?: return
        SautiIncomingCallScreen(
            callerName = config.callerNameResolver(incoming),
            colors = config.colors,
            typography = config.typography,
            strings = config.strings,
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
        return if (incoming != null) config.callerNameResolver(incoming) else ""
    }

    private fun onAcceptClicked() {
        val incoming = incomingCall ?: return
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
        stopRing()
        startJoinTimeout()
        val onAccept = config.onAccept
        val onAfterAccept = config.onAfterAccept
        val defaults = config.sessionDefaults
        val appContext = applicationContext
        SautiCallHost.scope.launch {
            val ticket = runCatching { onAccept(incoming) }.getOrNull() ?: return@launch
            runCatching {
                SautiHostJoin.join(appContext, ticket, defaults)
                onAfterAccept(incoming)
            }
        }
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

    private fun onDeclineClicked() {
        val incoming = incomingCall
        stopRing()
        if (incoming != null) {
            val onDecline = config.onDecline
            SautiCallHost.scope.launch { runCatching { onDecline(incoming) } }
        }
        finish()
    }

    private fun onEndClicked() {
        CallForegroundService.stop(this)
    }

    private fun onRemoteFinish() {
        if (accepted.value) return
        if (isFinishing) return
        stopRing()
        finish()
    }

    private fun minimize() {
        moveTaskToBack(true)
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (renderedPhase) {
                    SautiHostPhase.INCOMING -> onDeclineClicked()
                    SautiHostPhase.CONNECTING -> minimize()
                    SautiHostPhase.IN_CALL -> minimize()
                    SautiHostPhase.ENDED -> finish()
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
    }

    override fun onStop() {
        stopRing()
        super.onStop()
    }

    override fun onDestroy() {
        stopRing()
        joinTimeoutJob?.cancel()
        joinTimeoutJob = null
        if (!isChangingConfigurations) releaseRegistration()
        super.onDestroy()
    }

    private fun isResume(intent: Intent): Boolean = intent.getBooleanExtra(EXTRA_RESUME, false)

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

        private const val JOIN_TIMEOUT_MS = 20_000L
        private const val ENDED_LINGER_MS = 1_200L
        private const val STATE_ACCEPTED = "io.sauti.ui.compose.STATE_ACCEPTED"
        private const val STATE_SAW_SESSION = "io.sauti.ui.compose.STATE_SAW_SESSION"
        private const val STATE_ABORTED = "io.sauti.ui.compose.STATE_ABORTED"
        private const val RESUME_REQUEST = 0x5A08

        fun resumeIntent(context: Context): Intent =
            Intent(context, SautiCallActivity::class.java).apply {
                putExtra(EXTRA_RESUME, true)
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
