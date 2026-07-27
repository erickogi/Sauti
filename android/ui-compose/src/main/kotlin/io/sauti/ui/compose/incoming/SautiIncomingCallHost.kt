package io.sauti.ui.compose.incoming

import android.content.ComponentName
import android.content.Context
import io.sauti.android.incoming.DefaultForegroundProbe
import io.sauti.android.incoming.ForegroundProbe
import io.sauti.android.incoming.SautiIncomingCall
import io.sauti.android.incoming.SautiIncomingCallPresenter
import io.sauti.android.ring.CallRole
import io.sauti.android.ring.RingOverrides
import io.sauti.android.ring.SautiRinger
import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import io.sauti.ui.compose.SautiColors
import io.sauti.ui.compose.SautiStrings
import io.sauti.ui.compose.SautiTypography
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow

object SautiIncomingCallHost {

    data class Config(
        val onAccept: (SautiIncomingCall) -> Unit,
        val onDecline: (SautiIncomingCall) -> Unit,
        val colors: SautiColors? = null,
        val typography: SautiTypography? = null,
        val strings: SautiStrings = SautiStrings()
    )

    @Volatile
    private var config: Config = Config(onAccept = {}, onDecline = {})

    @Volatile
    internal var ringFactory: (Context, SautiIncomingCall, RingOverrides) -> IncomingRing =
        { context, _, overrides -> SautiRingerIncomingRing(context, overrides) }

    fun configure(config: Config) {
        this.config = config
    }

    internal fun current(): Config = config
}

object SautiIncoming {

    fun presenter(
        context: Context,
        onAccept: (SautiIncomingCall) -> Unit,
        onDecline: (SautiIncomingCall) -> Unit,
        colors: SautiColors? = null,
        typography: SautiTypography? = null,
        strings: SautiStrings = SautiStrings(),
        foreground: ForegroundProbe = DefaultForegroundProbe(context)
    ): SautiIncomingCallPresenter {
        SautiIncomingCallHost.configure(
            SautiIncomingCallHost.Config(onAccept, onDecline, colors, typography, strings)
        )
        return SautiIncomingCallPresenter(
            context,
            ComponentName(context, SautiIncomingCallActivity::class.java),
            foreground
        )
    }
}

internal class SautiRingerIncomingRing(
    context: Context,
    overrides: RingOverrides
) : IncomingRing {

    private val scope = MainScope()
    private val state = MutableStateFlow(CallState(phase = CallPhase.CONNECTING))
    private val ringer = SautiRinger.create(context, scope, overrides)

    override fun start() {
        ringer.start(CallRole.INCOMING, state)
    }

    override fun stop() {
        ringer.stop()
        scope.cancel()
    }
}
