package app.lazydex.data.tracker.service

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DelayedTrackingItem(
    val mediaId: String,
    val lastProgressRead: Float? = null,
    val status: String? = null
)

class DelayedTrackingStore(context: Context) {

    /**
     * Preference file where queued tracking updates are stored.
     */
    private val preferences = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)

    fun addProgress(mediaId: String, lastProgressRead: Double) {
        val existing = getItem(mediaId)
        val currentMax = existing?.lastProgressRead ?: 0f
        if (lastProgressRead.toFloat() >= currentMax) {
            val updated = (existing ?: DelayedTrackingItem(mediaId = mediaId))
                .copy(lastProgressRead = lastProgressRead.toFloat())
            saveItem(mediaId, updated)
        }
    }

    fun addStatus(mediaId: String, status: String) {
        val existing = getItem(mediaId)
        val updated = (existing ?: DelayedTrackingItem(mediaId = mediaId))
            .copy(status = status)
        saveItem(mediaId, updated)
    }

    fun add(mediaId: String, lastProgressRead: Double) {
        addProgress(mediaId, lastProgressRead)
    }

    fun remove(mediaId: String) {
        preferences.edit {
            remove(mediaId)
        }
    }

    fun getItems(): List<DelayedTrackingItem> {
        return preferences.all.mapNotNull { (key, value) ->
            value?.let { parseItem(key, it) }
        }
    }

    private fun getItem(mediaId: String): DelayedTrackingItem? {
        val raw = preferences.all[mediaId] ?: return null
        return parseItem(mediaId, raw)
    }

    private fun parseItem(mediaId: String, raw: Any): DelayedTrackingItem? {
        return when (raw) {
            is Number -> DelayedTrackingItem(mediaId = mediaId, lastProgressRead = raw.toFloat())
            is String -> {
                try {
                    Json.decodeFromString<DelayedTrackingItem>(raw)
                } catch (e: Exception) {
                    raw.toFloatOrNull()?.let {
                        DelayedTrackingItem(mediaId = mediaId, lastProgressRead = it)
                    }
                }
            }
            else -> null
        }
    }

    private fun saveItem(mediaId: String, item: DelayedTrackingItem) {
        val jsonString = Json.encodeToString(item)
        preferences.edit {
            putString(mediaId, jsonString)
        }
    }
}
