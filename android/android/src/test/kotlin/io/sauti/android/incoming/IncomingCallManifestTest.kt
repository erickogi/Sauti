package io.sauti.android.incoming

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IncomingCallManifestTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun wakeAndFullScreenPermissionsAreDeclared() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = info.requestedPermissions?.toSet() ?: emptySet()
        assertTrue(permissions.contains("android.permission.USE_FULL_SCREEN_INTENT"))
        assertTrue(permissions.contains("android.permission.WAKE_LOCK"))
    }
}
