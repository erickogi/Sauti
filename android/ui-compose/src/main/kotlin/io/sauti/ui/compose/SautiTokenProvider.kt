package io.sauti.ui.compose

import io.sauti.android.incoming.SautiIncomingCall

sealed interface SautiTokenRequest {
    val metadata: Map<String, String>

    data class Outgoing(
        val target: String,
        override val metadata: Map<String, String>
    ) : SautiTokenRequest

    data class Accept(val call: SautiIncomingCall) : SautiTokenRequest {
        override val metadata: Map<String, String> get() = call.metadata
    }
}

fun interface SautiTokenProvider {
    suspend fun mint(request: SautiTokenRequest): SautiSessionTicket?
}

internal suspend fun resolveTicket(
    provider: SautiTokenProvider?,
    request: SautiTokenRequest,
    legacy: suspend () -> SautiSessionTicket?
): SautiSessionTicket? =
    if (provider != null) provider.mint(request) else legacy()
