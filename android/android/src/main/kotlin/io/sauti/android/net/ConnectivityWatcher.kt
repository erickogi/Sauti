package io.sauti.android.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import io.sauti.android.Startable

class ConnectivityWatcher(
    context: Context,
    private val onNetworkChanged: (NetworkEventKind) -> Unit
) : Startable {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var modernCallback: ConnectivityManager.NetworkCallback? = null

    override fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            startModern()
        } else {
            startLegacy()
        }
    }

    override fun stop() {
        modernCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        modernCallback = null
    }

    private fun startModern() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onNetworkChanged(NetworkEventKind.Available)
            }

            override fun onLost(network: Network) {
                onNetworkChanged(NetworkEventKind.Lost)
            }
        }
        modernCallback = callback
        connectivityManager.registerNetworkCallback(request, callback)
    }

    private fun startLegacy() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onNetworkChanged(NetworkEventKind.Available)
            }

            override fun onLost(network: Network) {
                onNetworkChanged(NetworkEventKind.Lost)
            }
        }
        modernCallback = callback
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().build(),
            callback
        )
    }
}
