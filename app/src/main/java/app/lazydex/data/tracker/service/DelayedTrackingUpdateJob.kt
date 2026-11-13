package app.lazydex.data.tracker.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.lazydex.data.tracker.sync.AnilistSyncEngine
import app.lazydex.data.tracker.sync.TrackChapter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class DelayedTrackingUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams), KoinComponent {

    private val delayedTrackingStore: DelayedTrackingStore by inject()
    private val trackChapter: TrackChapter by inject()
    private val syncEngine: AnilistSyncEngine by inject()

    override suspend fun doWork(): Result {
        if (runAttemptCount > 3) {
            return Result.failure()
        }

        val items = delayedTrackingStore.getItems()
        for (item in items) {
            var anySuccess = false
            if (item.status != null) {
                val pushed = syncEngine.pushDirtyItem(item.mediaId)
                if (pushed) anySuccess = true
            }
            if (item.lastProgressRead != null) {
                trackChapter.await(context, item.mediaId, item.lastProgressRead.toDouble(), setupJobOnFailure = false)
                anySuccess = true
            }
            if (anySuccess) {
                delayedTrackingStore.remove(item.mediaId)
            }
        }

        // Also sweep any remaining dirty links
        syncEngine.pushDirtyLinks()

        return if (delayedTrackingStore.getItems().isEmpty()) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "DelayedTrackingUpdate"

        fun setupTask(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<DelayedTrackingUpdateJob>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                TAG,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
