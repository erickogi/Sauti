package io.sauti.android

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.net.ConnectivityWatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConnectivityWatcherTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun networkChangeInvokesReconnectEntryPoint() {
        var fired = 0
        val watcher = ConnectivityWatcher(context) { fired += 1 }
        watcher.start()

        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callbacks = shadowOf(manager).networkCallbacks
        assertTrue(callbacks.isNotEmpty())
        callbacks.first().onAvailable(ShadowNetwork.newInstance(42))

        assertTrue(fired >= 1)
        watcher.stop()
    }
}
