package io.sauti.android.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

data class ResumeRecord(
    val roomId: String,
    val participantId: String,
    val token: String,
    val url: String,
    val slotGeneration: Long
)

private val Context.resumeDataStore: DataStore<Preferences> by preferencesDataStore(name = "sauti_resume")

class ResumeStore(context: Context) {
    private val store = context.applicationContext.resumeDataStore

    suspend fun save(record: ResumeRecord) {
        store.edit { prefs ->
            prefs[KEY_ROOM] = record.roomId
            prefs[KEY_PARTICIPANT] = record.participantId
            prefs[KEY_TOKEN] = record.token
            prefs[KEY_URL] = record.url
            prefs[KEY_SLOT] = record.slotGeneration
        }
    }

    suspend fun load(): ResumeRecord? {
        val prefs = store.data.first()
        val roomId = prefs[KEY_ROOM] ?: return null
        val participantId = prefs[KEY_PARTICIPANT] ?: return null
        val token = prefs[KEY_TOKEN] ?: return null
        val url = prefs[KEY_URL] ?: return null
        val slot = prefs[KEY_SLOT] ?: 0L
        return ResumeRecord(
            roomId = roomId,
            participantId = participantId,
            token = token,
            url = url,
            slotGeneration = slot
        )
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val KEY_ROOM = stringPreferencesKey("roomId")
        val KEY_PARTICIPANT = stringPreferencesKey("participantId")
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_URL = stringPreferencesKey("url")
        val KEY_SLOT = longPreferencesKey("slotGeneration")
    }
}
