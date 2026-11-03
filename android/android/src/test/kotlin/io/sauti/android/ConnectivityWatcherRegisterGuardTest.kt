package io.sauti.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkRequest
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.net.ConnectivityWatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowConnectivityManager

@Implements(ConnectivityManager::class)
class RegisterThrowingConnectivityShadow : ShadowConnectivityManager() {
    @Implementation
    override fun registerNetworkCallback(
        request: NetworkRequest,
        callback: ConnectivityManager.NetworkCallback
    ) {
        throw RuntimeException("too many requests")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [RegisterThrowingConnectivityShadow::class])
class ConnectivityWatcherRegisterGuardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun registrationFailureDoesNotCrashStartOrStop() {
        val watcher = ConnectivityWatcher(context) { }

        watcher.start()
        watcher.stop()
    }
}
