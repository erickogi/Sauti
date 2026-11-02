package io.sauti.android.ring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FirstAvailableTest {

    @Test
    fun returnsFirstNonNullValue() {
        assertEquals("a", firstAvailable({ "a" }, { "b" }))
    }

    @Test
    fun skipsNullSourceAndReturnsNext() {
        assertEquals("b", firstAvailable({ null }, { "b" }))
    }

    @Test
    fun skipsThrowingSourceAndReturnsNext() {
        assertEquals("fallback", firstAvailable({ throw SecurityException("WRITE_SETTINGS") }, { "fallback" }))
    }

    @Test
    fun returnsNullWhenAllSourcesNull() {
        assertNull(firstAvailable<String>({ null }, { null }))
    }

    @Test
    fun returnsNullWhenAllSourcesThrow() {
        assertNull(firstAvailable<String>({ throw SecurityException() }, { throw IllegalStateException() }))
    }

    @Test
    fun stopsAtFirstNonNullAndDoesNotInvokeLaterSources() {
        var laterInvoked = false
        val result = firstAvailable({ "first" }, { laterInvoked = true; "second" })
        assertEquals("first", result)
        assertFalse(laterInvoked)
    }

    @Test
    fun throwingFirstSourcesFallBackToConstant() {
        val result = firstAvailable(
            { throw SecurityException("com.safeboda.passenger was not granted WRITE_SETTINGS") },
            { throw SecurityException("notification also unavailable") },
            { "content://settings/system/ringtone" }
        )
        assertEquals("content://settings/system/ringtone", result)
    }
}
