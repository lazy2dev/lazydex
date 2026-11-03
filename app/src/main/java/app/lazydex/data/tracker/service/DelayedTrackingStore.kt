package app.lazydex.data.tracker.service

import android.content.Context
import androidx.core.content.edit

class DelayedTrackingStore(context: Context) {

    /**
     * Preference file where queued tracking updates are stored.
     */
    private val preferences = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)

    fun add(mediaId: String, lastProgressRead: Double) {
        val previous = preferences.getFloat(mediaId, 0f)
        if (lastProgressRead > previous) {
            preferences.edit {
                putFloat(mediaId, lastProgressRead.toFloat())
            }
        }
    }

    fun remove(mediaId: String) {
        preferences.edit {
            remove(mediaId)
        }
    }

    fun getItems(): List<DelayedTrackingItem> {
        return preferences.all.mapNotNull {
            DelayedTrackingItem(
                mediaId = it.key,
                lastProgressRead = it.value.toString().toFloat(),
            )
        }
    }

    data class DelayedTrackingItem(
        val mediaId: String,
        val lastProgressRead: Float,
    )
}

