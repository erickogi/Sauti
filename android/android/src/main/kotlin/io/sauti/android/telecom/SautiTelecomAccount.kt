package io.sauti.android.telecom

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.O)
class SautiTelecomAccount(context: Context) {

    private val appContext = context.applicationContext
    private val telecomManager = appContext.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    val handle: PhoneAccountHandle = PhoneAccountHandle(
        ComponentName(appContext, SautiConnectionService::class.java),
        ACCOUNT_ID
    )

    fun register(label: String) {
        val account = PhoneAccount.builder(handle, label)
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .build()
        telecomManager.registerPhoneAccount(account)
    }

    fun unregister() {
        telecomManager.unregisterPhoneAccount(handle)
    }

    @SuppressLint("MissingPermission")
    fun place(participantId: String) {
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        }
        telecomManager.placeCall(addressOf(participantId), extras)
    }

    @SuppressLint("MissingPermission")
    fun reportIncoming(participantId: String) {
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, addressOf(participantId))
        }
        telecomManager.addNewIncomingCall(handle, extras)
    }

    private fun addressOf(participantId: String): Uri = Uri.fromParts(SCHEME, participantId, null)

    companion object {
        const val ACCOUNT_ID = "sauti-self-managed"
        const val SCHEME = "sauti"
    }
}
