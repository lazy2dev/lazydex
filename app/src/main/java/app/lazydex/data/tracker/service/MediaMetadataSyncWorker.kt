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
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.tracker.anilist.AnilistApi
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.anilist.TokenBucketLimiter
import app.lazydex.util.TextSanitizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MediaMetadataSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), KoinComponent {

    private val client: AnilistApi by inject()
    private val mediaItemDao: MediaItemDao by inject()
    private val mediaLinkDao: MediaLinkDao by inject()
    private val tokenStore: AnilistTokenStore by inject()

    override suspend fun doWork(): Result {
        if (!tokenStore.isLoggedIn) {
            return Result.success()
        }

        var batchCount = 0
        while (batchCount < MAX_BATCHES) {
            val pendingLinks = mediaLinkDao.getLinksNeedingMetadata(PROVIDER_ID, limit = BATCH_SIZE)
            if (pendingLinks.isEmpty()) {
                break
            }
            batchCount++

            val targets = pendingLinks.mapNotNull { link ->
                val item = mediaItemDao.getById(link.mediaId) ?: return@mapNotNull null
                val remoteId = link.remoteId?.toIntOrNull() ?: return@mapNotNull null
                Triple(link, item, remoteId)
            }

            if (targets.isEmpty()) {
                val orphanRemoteIds = pendingLinks.mapNotNull { it.remoteId }
                if (orphanRemoteIds.isNotEmpty()) {
                    mediaLinkDao.markMetadataFetched(PROVIDER_ID, orphanRemoteIds, System.currentTimeMillis())
                }
                continue
            }

            val targetsByType = targets.groupBy { (_, item, _) ->
                if (item.type.equals("ANIME", ignoreCase = true)) "ANIME" else "MANGA"
            }

            val allRequestedRemoteIds = mutableListOf<String>()

            for ((aniType, group) in targetsByType) {
                val ids = group.map { it.third }
                val requestedInGroup = group.map { it.first.remoteId ?: "" }.filter { it.isNotBlank() }
                allRequestedRemoteIds.addAll(requestedInGroup)

                val remoteMetadataList = try {
                    client.getMediaBatchMetadata(
                        ids = ids,
                        type = aniType,
                        priority = TokenBucketLimiter.Priority.BACKGROUND
                    )
                } catch (e: IOException) {
                    return Result.retry()
                }

                val metadataMap = remoteMetadataList.associateBy { it.id }

                for ((_, item, remoteId) in group) {
                    val remote = metadataMap[remoteId]
                    if (remote != null) {
                        val sanitized = TextSanitizer.sanitizeHtml(remote.description)
                        val tagsJson = if (remote.tags.isNotEmpty()) {
                            Json.encodeToString(remote.tags.map { it.name })
                        } else null
                        val genresJson = if (remote.genres.isNotEmpty()) {
                            Json.encodeToString(remote.genres)
                        } else null

                        mediaItemDao.updateMetadata(
                            id = item.id,
                            description = sanitized.ifBlank { null },
                            tags = tagsJson,
                            genres = genresJson
                        )
                    }
                }
            }

            if (allRequestedRemoteIds.isNotEmpty()) {
                mediaLinkDao.markMetadataFetched(PROVIDER_ID, allRequestedRemoteIds, System.currentTimeMillis())
            }
        }

        val remaining = mediaLinkDao.getLinksNeedingMetadata(PROVIDER_ID, limit = 1)
        if (remaining.isNotEmpty()) {
            setupTask(context)
        }

        return Result.success()
    }

    companion object {
        const val TAG = "MediaMetadataSyncWorker"
        const val PROVIDER_ID = "anilist"
        const val BATCH_SIZE = 50
        const val MAX_BATCHES = 10

        fun setupTask(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<MediaMetadataSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                TAG,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
