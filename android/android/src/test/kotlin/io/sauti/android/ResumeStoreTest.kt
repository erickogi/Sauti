package io.sauti.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.persistence.ResumeRecord
import io.sauti.android.persistence.ResumeStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ResumeStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun recordSurvivesProcessRestartAndClears() = runBlocking {
        val record = ResumeRecord(
            roomId = "room-1",
            participantId = "p-1",
            token = "token-A",
            url = "wss://example",
            slotGeneration = 7
        )
        ResumeStore(context).save(record)

        val recovered = ResumeStore(context).load()
        assertEquals(record, recovered)

        ResumeStore(context).clear()
        assertNull(ResumeStore(context).load())
    }
}
