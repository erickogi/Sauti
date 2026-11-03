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

@Implements(TelephonyManager::class)
class RegisterThrowingTelephonyShadow : ShadowTelephonyManager() {
    @Implementation
    fun registerTelephonyCallback(executor: Executor, callback: TelephonyCallback) {
        throw RuntimeException("too many requests")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [RegisterThrowingTelephonyShadow::class])
class TelephonyWatcherRegisterGuardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun registrationFailureDoesNotCrashStartOrStop() {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(android.Manifest.permission.READ_PHONE_STATE)
        val watcher = TelephonyWatcher(context) { }

        watcher.start()
        watcher.stop()
    }
}
