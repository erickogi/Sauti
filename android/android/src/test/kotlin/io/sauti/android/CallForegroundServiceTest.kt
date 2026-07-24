package io.sauti.android

import android.content.Intent
import android.content.pm.ServiceInfo
import io.sauti.android.service.CallForegroundService
import io.sauti.android.service.CallNotification
import io.sauti.android.service.CallPresence
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowService
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallForegroundServiceTest {

    @Test
    fun startsMicrophoneTypedForegroundNotification() {
        val app = RuntimeEnvironment.getApplication()
        val intent = Intent(app, CallForegroundService::class.java).apply {
            putExtra(CallForegroundService.EXTRA_TITLE, "Session")
            putExtra(CallForegroundService.EXTRA_PRESENCE, CallPresence.ONGOING.name)
        }

        val service = Robolectric.buildService(CallForegroundService::class.java, intent).create().get()
        service.onStartCommand(intent, 0, 1)

        val shadow = shadowOf(service)
        assertNotNull(shadow.lastForegroundNotification)
        assertEquals(CallNotification.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, foregroundServiceTypeOf(shadow))
    }

    private fun foregroundServiceTypeOf(shadow: ShadowService): Int {
        val method = ShadowService::class.java.getDeclaredMethod("getForegroundServiceType")
        method.isAccessible = true
        return method.invoke(shadow) as Int
    }
}
