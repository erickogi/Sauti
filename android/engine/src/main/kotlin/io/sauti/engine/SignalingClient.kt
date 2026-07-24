package io.sauti.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface SignalingHooks {
    fun onFrame(frame: ServerFrame)
    fun onVersionMismatch(version: Int?)
    fun onReconnecting()
    fun onTerminal(reason: String)
}

data class SignalingConfig(
    val maxReconnectAttempts: Int,
    val reconnectBaseMs: Long,
    val reconnectMaxMs: Long,
    val random: () -> Double
)

class SignalingClient(
    private val scope: CoroutineScope,
    private val factory: SignalingTransportFactory,
    private val config: SignalingConfig,
    private val hooks: SignalingHooks
) {
    private var transport: SignalingTransport? = null
    private var url: String = ""
    private var token: String = ""
    private var attempt: Int = 0
    private var intentional: Boolean = false
    private var terminated: Boolean = false
    private var reconnectJob: Job? = null

    fun connect(url: String, token: String) {
        this.url = url
        this.token = token
        this.intentional = false
        this.terminated = false
        this.attempt = 0
        open()
    }

    private fun open() {
        val callbacks = object : TransportCallbacks {
            override fun onOpen() = Unit

            override fun onMessage(text: String) {
                receive(text)
            }

            override fun onClosed(code: Int) {
                handleClose()
            }

            override fun onFailure(error: Throwable) {
                handleClose()
            }
        }
        val opened = factory.connect(url, callbacks)
        transport = opened
        opened.send(FrameCodec.encodeJoin(token))
    }

    private fun receive(text: String) {
        when (val result = FrameCodec.parse(text)) {
            is FrameParseResult.VersionMismatch -> hooks.onVersionMismatch(result.version)
            is FrameParseResult.Invalid -> Unit
            is FrameParseResult.Parsed -> {
                if (result.frame is ServerFrame.Ready) attempt = 0
                hooks.onFrame(result.frame)
            }
        }
    }

    private fun handleClose() {
        transport = null
        if (intentional || terminated) return
        if (attempt >= config.maxReconnectAttempts) {
            terminated = true
            hooks.onTerminal("reconnection gave up after the attempt budget")
            return
        }
        hooks.onReconnecting()
        val delayMs = computeBackoff(attempt, config.reconnectBaseMs, config.reconnectMaxMs, config.random)
        attempt += 1
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!intentional && !terminated) open()
        }
    }

    fun send(text: String) {
        transport?.send(text)
    }

    fun close() {
        intentional = true
        reconnectJob?.cancel()
        reconnectJob = null
        val current = transport
        transport = null
        current?.close()
    }
}
