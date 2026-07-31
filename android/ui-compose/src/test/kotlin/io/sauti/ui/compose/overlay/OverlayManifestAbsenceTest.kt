package io.sauti.ui.compose.overlay

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class OverlayManifestAbsenceTest {

    @Test
    fun systemAlertWindowIsAbsentFromModuleManifest() {
        val manifest = locateManifest()
        assertFalse(manifest.readText().contains("SYSTEM_ALERT_WINDOW"))
    }

    private fun locateManifest(): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(userDir, "src/main/AndroidManifest.xml"),
            File(userDir, "ui-compose/src/main/AndroidManifest.xml")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("ui-compose AndroidManifest.xml not found from " + userDir.path)
    }
}
