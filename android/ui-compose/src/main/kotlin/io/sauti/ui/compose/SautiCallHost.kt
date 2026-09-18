package io.sauti.ui.compose

import android.Manifest
import android.content.Context
import androidx.compose.runtime.Composable
import io.sauti.android.audio.AudioDevice
import io.sauti.android.incoming.IncomingCallOverrides
import io.sauti.android.incoming.SautiIncomingCall
import io.sauti.android.ring.RingOverrides
import io.sauti.engine.AudioProcessingConfig
import io.sauti.engine.QoeSample
import io.sauti.ui.compose.push.SautiPushKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

data class SautiSessionTicket(
    val url: String,
    val token: String,
    val roomId: String,
    val participantId: String,
    val displayTitle: String,
    val slotGeneration: Long = 1L,
    val endWhenLastPeerLeaves: Boolean = true,
    val initialDevice: AudioDevice = AudioDevice.EARPIECE,
    val callId: String = ""
)

data class SautiOutgoingRequest(
    val target: String,
    val metadata: Map<String, String> = emptyMap()
)

data class SautiSessionDefaults(
    val enableProximity: Boolean = true,
    val audioProcessing: AudioProcessingConfig = AudioProcessingConfig(
        autoGainControl = true,
        highpassFilter = true
    ),
    val iceRestartDebounceMs: Long = 2_000L,
    val onQoe: (QoeSample) -> Unit = {}
)

fun defaultCallerName(call: SautiIncomingCall): String {
    val fromField = call.callerName?.takeIf { it.isNotBlank() }
    val fromMetadata = call.metadata["name"]?.takeIf { it.isNotBlank() }
    return fromField ?: fromMetadata ?: ""
}

internal fun hostAppCallerLabel(context: Context): String {
    val label = runCatching {
        context.applicationInfo.loadLabel(context.packageManager).toString().trim()
    }.getOrNull().orEmpty()
    return if (label.isBlank()) "Call" else "$label Call"
}

object SautiCallHost {

    data class Config(
        val onAccept: suspend (SautiIncomingCall) -> SautiSessionTicket?,
        val onDecline: suspend (SautiIncomingCall) -> Unit,
        val callerNameResolver: (SautiIncomingCall) -> String = ::defaultCallerName,
        val colors: SautiColors? = null,
        val typography: SautiTypography? = null,
        val strings: SautiStrings = SautiStrings(),
        val acceptPermissions: List<String> = listOf(Manifest.permission.RECORD_AUDIO),
        val onAcceptPermissionDenied: () -> Unit = {},
        val onAfterAccept: (SautiIncomingCall) -> Unit = {},
        val ringOverrides: RingOverrides = RingOverrides(),
        val notificationOverrides: IncomingCallOverrides = IncomingCallOverrides(),
        val autoMuteOnCellularCall: Boolean = false,
        val sessionDefaults: SautiSessionDefaults = SautiSessionDefaults(),
        val onStartCall: suspend (SautiOutgoingRequest) -> SautiSessionTicket? = { null },
        val onCancelCall: suspend (callId: String) -> Unit = {},
        val onEndCall: suspend (callId: String) -> Unit = {},
        val outgoingNoAnswerTimeoutMs: Long = 35_000L,
        val onOutgoingFailed: () -> Unit = {},
        val overlayPromptContent: (@Composable (onConfirm: () -> Unit, onDismiss: () -> Unit) -> Unit)? = null,
        val incomingRequiresSlide: Boolean = true,
        val pushKeys: SautiPushKeys = SautiPushKeys(),
        val tokenProvider: SautiTokenProvider? = null
    )

    @Volatile
    private var config: Config? = null

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun configure(config: Config) {
        this.config = config
    }

    internal fun optional(): Config? = config

    internal fun require(): Config =
        config ?: error("SautiCallHost.configure must run before a call is shown")
}
