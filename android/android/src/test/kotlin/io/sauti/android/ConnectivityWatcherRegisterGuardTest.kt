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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Implements(ConnectivityManager::class)
class RegisterThrowingConnectivityShadow : ShadowConnectivityManager() {
    private var calls = 0

    @Implementation
    override fun registerNetworkCallback(
        request: NetworkRequest,
        callback: ConnectivityManager.NetworkCallback
    ) {
        calls += 1
        if (calls == 1) throw RuntimeException("too many requests")
        super.registerNetworkCallback(request, callback)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [RegisterThrowingConnectivityShadow::class])
class ConnectivityWatcherRegisterGuardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun callbackOf(watcher: ConnectivityWatcher): Any? {
        val field = ConnectivityWatcher::class.java.getDeclaredField("modernCallback")
        field.isAccessible = true
        return field.get(watcher)
    }

    @Test
    fun registrationFailureLeavesNoCallbackAndAllowsRetry() {
        val watcher = ConnectivityWatcher(context) { }

        watcher.start()
        assertNull(callbackOf(watcher), "a failed register must not leave a dangling callback")

        watcher.start()
        assertNotNull(callbackOf(watcher), "a retry after a failed register must succeed")

        watcher.stop()
        assertNull(callbackOf(watcher))
    }
}
