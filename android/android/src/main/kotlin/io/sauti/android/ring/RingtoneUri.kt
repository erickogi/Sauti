package io.sauti.android.ring

internal fun <T : Any> firstAvailable(vararg sources: () -> T?): T? {
    for (source in sources) {
        val value = runCatching { source() }.getOrNull()
        if (value != null) return value
    }
    return null
}
