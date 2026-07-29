package io.sauti.android.telecom

import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import androidx.annotation.RequiresApi

fun interface SautiConnectionProvider {
    fun create(request: ConnectionRequest?): Connection?
}

@RequiresApi(Build.VERSION_CODES.O)
class SautiConnectionService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? = provider?.create(request)

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? = provider?.create(request)

    companion object {
        @Volatile
        var provider: SautiConnectionProvider? = null
    }
}
