package io.sauti.ui.compose

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals

class SautiCallPermissionsTest {

    private val record = Manifest.permission.RECORD_AUDIO
    private val notifications = Manifest.permission.POST_NOTIFICATIONS

    @Test
    fun addsNotificationsOnApi33() {
        assertEquals(
            listOf(record, notifications),
            SautiCallPermissions.required(33, listOf(record))
        )
    }

    @Test
    fun omitsNotificationsBelowApi33() {
        assertEquals(listOf(record), SautiCallPermissions.required(32, listOf(record)))
    }

    @Test
    fun doesNotDuplicateNotificationsWhenAlreadyInBase() {
        assertEquals(
            listOf(record, notifications),
            SautiCallPermissions.required(33, listOf(record, notifications))
        )
    }

    @Test
    fun missingReturnsUngrantedInOrder() {
        assertEquals(
            listOf(notifications),
            SautiCallPermissions.missing(listOf(record, notifications), setOf(record))
        )
    }

    @Test
    fun missingEmptyWhenAllGranted() {
        assertEquals(
            emptyList(),
            SautiCallPermissions.missing(listOf(record, notifications), setOf(record, notifications))
        )
    }
}
