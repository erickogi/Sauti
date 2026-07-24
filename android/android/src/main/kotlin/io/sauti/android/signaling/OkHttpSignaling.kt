package io.sauti.android.signaling

import io.sauti.engine.SignalingTransport
import io.sauti.engine.SignalingTransportFactory
import io.sauti.engine.TransportCallbacks
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class OkHttpSignalingFactory(
    private val client: OkHttpClient = defaultClient()
) : SignalingTransportFactory {

    override fun connect(url: String, callbacks: TransportCallbacks): SignalingTransport {
        val request = Request.Builder().url(url).build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                callbacks.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                callbacks.onMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                callbacks.onClosed(code)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                callbacks.onFailure(t)
            }
        })
        return OkHttpTransport(socket)
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }
}

private class OkHttpTransport(private val socket: WebSocket) : SignalingTransport {
    override fun send(text: String) {
        socket.send(text)
    }

    override fun close() {
        socket.close(1000, "client leave")
    }
}
