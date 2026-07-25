package io.sauti.android.persistence

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ResumeRecord(
    val roomId: String,
    val participantId: String,
    val token: String,
    val url: String,
    val slotGeneration: Long
)

class ResumeStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    suspend fun save(record: ResumeRecord) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_ROOM, record.roomId)
            .putString(KEY_PARTICIPANT, record.participantId)
            .putString(KEY_TOKEN, record.token)
            .putString(KEY_URL, record.url)
            .putLong(KEY_SLOT, record.slotGeneration)
            .commit()
        Unit
    }

    suspend fun load(): ResumeRecord? = withContext(Dispatchers.IO) {
        val roomId = prefs.getString(KEY_ROOM, null) ?: return@withContext null
        val participantId = prefs.getString(KEY_PARTICIPANT, null) ?: return@withContext null
        val token = prefs.getString(KEY_TOKEN, null) ?: return@withContext null
        val url = prefs.getString(KEY_URL, null) ?: return@withContext null
        ResumeRecord(
            roomId = roomId,
            participantId = participantId,
            token = token,
            url = url,
            slotGeneration = prefs.getLong(KEY_SLOT, 0L)
        )
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().commit()
        Unit
    }

    private companion object {
        const val STORE_NAME = "sauti_resume"
        const val KEY_ROOM = "roomId"
        const val KEY_PARTICIPANT = "participantId"
        const val KEY_TOKEN = "token"
        const val KEY_URL = "url"
        const val KEY_SLOT = "slotGeneration"
    }
}
