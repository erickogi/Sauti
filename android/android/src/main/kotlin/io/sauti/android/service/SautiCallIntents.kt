package io.sauti.android.service

import android.app.PendingIntent

data class SautiCallIntents(
    val contentIntent: PendingIntent,
    val hangupIntent: PendingIntent? = null
)
