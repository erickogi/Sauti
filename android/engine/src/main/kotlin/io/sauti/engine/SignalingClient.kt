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
    private val lock = Any()
    private var transport: SignalingTransport? = null
    private var url: String = ""
    private var token: String = ""
    private var attempt: Int = 0
    private var intentional: Boolean = false
    private var terminated: Boolean = false
    private var reconnectJob: Job? = null

    fun connect(url: String, token: String) {
        synchronized(lock) {
            this.url = url
            this.token = token
            this.intentional = false
            this.terminated = false
            this.attempt = 0
        }
        open()
    }

    private fun open() {
        val target = synchronized(lock) { url to token }
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
        val opened = factory.connect(target.first, callbacks)
        synchronized(lock) { transport = opened }
        opened.send(FrameCodec.encodeJoin(target.second))
    }

    private fun receive(text: String) {
        when (val result = FrameCodec.parse(text)) {
            is FrameParseResult.VersionMismatch -> hooks.onVersionMismatch(result.version)
            is FrameParseResult.Invalid -> Unit
            is FrameParseResult.Parsed -> {
                if (result.frame is ServerFrame.Ready) synchronized(lock) { attempt = 0 }
                hooks.onFrame(result.frame)
            }
        }
    }

    private fun handleClose() {
        var terminalReason: String? = null
        var reconnecting = false
        synchronized(lock) {
            transport = null
            if (intentional || terminated) return
            if (attempt >= config.maxReconnectAttempts) {
                terminated = true
                terminalReason = "reconnection gave up after the attempt budget"
            } else {
                val delayMs = computeBackoff(attempt, config.reconnectBaseMs, config.reconnectMaxMs, config.random)
                attempt += 1
                reconnecting = true
                reconnectJob = scope.launch {
                    delay(delayMs)
                    val resume = synchronized(lock) { !intentional && !terminated }
                    if (resume) open()
                }
            }
        }
        terminalReason?.let { hooks.onTerminal(it) }
        if (reconnecting) hooks.onReconnecting()
    }

    fun send(text: String) {
        val current = synchronized(lock) { transport }
        current?.send(text)
    }

    fun close() {
        val current = synchronized(lock) {
            intentional = true
            reconnectJob?.cancel()
            reconnectJob = null
            val t = transport
            transport = null
            t
        }
        current?.close()
    }
}
