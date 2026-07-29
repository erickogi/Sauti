package io.sauti.android.telecom

import android.content.Context
import io.sauti.android.CallEngine
import io.sauti.android.SautiClient
import io.sauti.android.Startable
import io.sauti.android.WebRtcCallEngine
import io.sauti.android.audio.AudioController
import io.sauti.android.net.ConnectivityPolicy
import io.sauti.android.net.ConnectivityWatcher
import io.sauti.android.persistence.ResumeStore
import io.sauti.engine.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private class InertStartable : Startable {
    override fun start() = Unit
    override fun stop() = Unit
}

object SautiTelecomClient {

    fun build(
        context: Context,
        engineConfig: EngineConfig = EngineConfig(),
        scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
        connectivityPolicy: ConnectivityPolicy = ConnectivityPolicy(),
        enableProximity: Boolean = false
    ): SautiClient = build(
        context = context,
        scope = scope,
        connectivityPolicy = connectivityPolicy,
        enableProximity = enableProximity,
        audio = TelecomAudioController(context.applicationContext),
        engine = WebRtcCallEngine(context.applicationContext, engineConfig, scope)
    )

    internal fun build(
        context: Context,
        scope: CoroutineScope,
        connectivityPolicy: ConnectivityPolicy,
        enableProximity: Boolean,
        audio: AudioController,
        engine: CallEngine
    ): SautiClient = SautiClient(
        context = context,
        scope = scope,
        audio = audio,
        engine = engine,
        resumeStore = ResumeStore(context.applicationContext),
        telephonyFactory = { _, _ -> InertStartable() },
        connectivityFactory = { ctx, cb -> ConnectivityWatcher(ctx, cb) },
        connectivityPolicy = connectivityPolicy,
        enableProximity = enableProximity
    )
}
