package io.sauti.android.overlay

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OverlayManifestTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun systemAlertWindowIsAbsentFromBaseManifest() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = info.requestedPermissions?.toSet() ?: emptySet()
        assertFalse(permissions.contains("android.permission.SYSTEM_ALERT_WINDOW"))
    }
}
