package io.sauti.android

import android.content.Context
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.telephony.TelephonyWatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowTelephonyManager
import java.util.concurrent.Executor
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Implements(TelephonyManager::class)
class RegisterThrowingTelephonyShadow : ShadowTelephonyManager() {
    private var calls = 0

    @Implementation
    fun registerTelephonyCallback(executor: Executor, callback: TelephonyCallback) {
        calls += 1
        if (calls == 1) throw RuntimeException("too many requests")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [RegisterThrowingTelephonyShadow::class])
class TelephonyWatcherRegisterGuardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun callbackOf(watcher: TelephonyWatcher): Any? {
        val field = TelephonyWatcher::class.java.getDeclaredField("modernCallback")
        field.isAccessible = true
        return field.get(watcher)
    }

    @Test
    fun registrationFailureLeavesNoCallbackAndAllowsRetry() {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(android.Manifest.permission.READ_PHONE_STATE)
        val watcher = TelephonyWatcher(context) { }

        watcher.start()
        assertNull(callbackOf(watcher), "a failed register must not leave a dangling callback")

        watcher.start()
        assertNotNull(callbackOf(watcher), "a retry after a failed register must succeed")

        watcher.stop()
        assertNull(callbackOf(watcher))
    }
}
