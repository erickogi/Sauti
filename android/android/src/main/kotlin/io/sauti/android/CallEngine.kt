package io.sauti.android

import android.content.Context
import io.sauti.android.rtc.WebRtcFactory
import io.sauti.android.signaling.OkHttpSignalingFactory
import io.sauti.engine.AudioProcessingConfig
import io.sauti.engine.CallEvent
import io.sauti.engine.CallSession
import io.sauti.engine.CallState
import io.sauti.engine.EngineConfig
import io.sauti.engine.JoinConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface CallEngine {
    val state: StateFlow<CallState>
    val events: SharedFlow<CallEvent>
    suspend fun join(config: JoinConfig)
    fun setMuted(muted: Boolean)
    fun setHold(onHold: Boolean)
    fun onNetworkChanged()
    fun leave()
    fun dispose()
}

class WebRtcCallEngine(
    context: Context,
    engineConfig: EngineConfig,
    scope: CoroutineScope,
    audioProcessing: AudioProcessingConfig = AudioProcessingConfig()
) : CallEngine {
    private val rtcFactory = WebRtcFactory(context.applicationContext, audioProcessing)
    private val session = CallSession(
        rtcFactory = rtcFactory,
        transportFactory = OkHttpSignalingFactory(),
        localMedia = rtcFactory,
        config = engineConfig,
        scope = scope
    )

    override val state: StateFlow<CallState> get() = session.state
    override val events: SharedFlow<CallEvent> get() = session.eventFlow

    override suspend fun join(config: JoinConfig) = session.join(config)

    override fun setMuted(muted: Boolean) = session.setMuted(muted)

    override fun setHold(onHold: Boolean) = session.setHold(onHold)

    override fun onNetworkChanged() = session.onNetworkChanged()

    override fun leave() = session.leave()

    override fun dispose() = rtcFactory.dispose()
}
