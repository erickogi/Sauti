package io.sauti.android

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.telephony.TelephonyWatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TelephonyWatcherTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun offhookAndRingingRouteIntoInterruptedPath() {
        val states = mutableListOf<Boolean>()
        val watcher = TelephonyWatcher(context) { states.add(it) }

        dispatch(watcher, TelephonyManager.CALL_STATE_OFFHOOK)
        dispatch(watcher, TelephonyManager.CALL_STATE_RINGING)
        dispatch(watcher, TelephonyManager.CALL_STATE_IDLE)

        assertEquals(listOf(true, true, false), states)
    }

    @Test
    fun startIsPermissionGatedGracefully() {
        val watcher = TelephonyWatcher(context) { }
        watcher.start()
        watcher.stop()
        assertTrue(true)
    }

    @Test
    @Config(sdk = [28])
    fun legacyListenerRegistersAndRoutesCallState() {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(android.Manifest.permission.READ_PHONE_STATE)
        val states = mutableListOf<Boolean>()
        val watcher = TelephonyWatcher(context) { states.add(it) }
        watcher.start()

        val listener = legacyListenerOf(watcher)
        assertNotNull(listener)
        states.clear()
        listener.onCallStateChanged(TelephonyManager.CALL_STATE_OFFHOOK, null)
        listener.onCallStateChanged(TelephonyManager.CALL_STATE_IDLE, null)
        watcher.stop()

        assertEquals(listOf(true, false), states)
    }

    @Test
    fun modernStartIsIdempotentAndReArmsWithoutLeaking() {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(android.Manifest.permission.READ_PHONE_STATE)
        val watcher = TelephonyWatcher(context) { }

        watcher.start()
        watcher.start()
        assertEquals(1, callbackRegistrationCount())

        watcher.stop()
        assertEquals(0, callbackRegistrationCount())
    }

    @Test
    fun startArmsAfterPermissionBecomesAvailable() {
        val app = RuntimeEnvironment.getApplication()
        val watcher = TelephonyWatcher(context) { }

        watcher.start()
        assertEquals(0, callbackRegistrationCount())

        shadowOf(app).grantPermissions(android.Manifest.permission.READ_PHONE_STATE)
        watcher.start()
        assertEquals(1, callbackRegistrationCount())

        watcher.stop()
    }

    private fun callbackRegistrationCount(): Int {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val shadow = shadowOf(telephonyManager)
        val field = shadow.javaClass.getDeclaredField("telephonyCallbackRegistrations")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (field.get(shadow) as List<Any>).size
    }

    private fun dispatch(watcher: TelephonyWatcher, state: Int) {
        val method = TelephonyWatcher::class.java.getDeclaredMethod("dispatch", Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(watcher, state)
    }

    private fun legacyListenerOf(watcher: TelephonyWatcher): PhoneStateListener? {
        val field = TelephonyWatcher::class.java.getDeclaredField("legacyListener")
        field.isAccessible = true
        return field.get(watcher) as PhoneStateListener?
    }
}
