package app.lazydex.data.tracker.sync

import android.content.Context
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.service.DelayedTrackingStore
import app.lazydex.data.tracker.service.DelayedTrackingUpdateJob

class TrackChapter(
    private val syncEngine: AnilistSyncEngine,
    private val tokenStore: AnilistTokenStore,
    private val delayedTrackingStore: DelayedTrackingStore,
) {

    /**
     * Pushes chapter/episode progress to AniList immediately.
     * If request fails or device is offline, saves to [DelayedTrackingStore]
     * and schedules [DelayedTrackingUpdateJob] via WorkManager.
     */
    suspend fun await(
        context: Context,
        mediaId: String,
        progress: Double,
        setupJobOnFailure: Boolean = true
    ) {
        if (!tokenStore.isLoggedIn) return

        try {
            val success = syncEngine.pushDirtyItem(mediaId)
            if (success) {
                delayedTrackingStore.remove(mediaId)
            } else {
                delayedTrackingStore.add(mediaId, progress)
                if (setupJobOnFailure) {
                    DelayedTrackingUpdateJob.setupTask(context)
                }
            }
        } catch (e: Exception) {
            delayedTrackingStore.add(mediaId, progress)
            if (setupJobOnFailure) {
                DelayedTrackingUpdateJob.setupTask(context)
            }
        }
    }
}

