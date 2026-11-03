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

    fun register(label: String): Boolean {
        val account = PhoneAccount.builder(handle, label)
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .build()
        return runCatching { telecomManager.registerPhoneAccount(account) }.isSuccess
    }

    fun unregister() {
        runCatching { telecomManager.unregisterPhoneAccount(handle) }
    }

    @SuppressLint("MissingPermission")
    fun place(participantId: String): Boolean {
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        }
        return runCatching { telecomManager.placeCall(addressOf(participantId), extras) }.isSuccess
    }

    @SuppressLint("MissingPermission")
    fun reportIncoming(participantId: String): Boolean {
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, addressOf(participantId))
        }
        return runCatching { telecomManager.addNewIncomingCall(handle, extras) }.isSuccess
    }

    private fun addressOf(participantId: String): Uri = Uri.fromParts(SCHEME, participantId, null)

    companion object {
        const val ACCOUNT_ID = "sauti-self-managed"
        const val SCHEME = "sauti"
    }
}
