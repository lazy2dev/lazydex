package app.lazydex.data.tracker.sync

import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.tracker.anilist.dto.AniListMediaListEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Snapshot of the state recorded during the last successful synchronization.
 * Stored as serialized JSON in `media_links.syncedSnapshot`.
 */
@Serializable
data class SyncSnapshot(
    val remoteUpdatedAt: Long = 0L,      // AniList updatedAt (epoch seconds)
    val localUpdatedAt: Long = 0L,       // LazyDex updatedAt (epoch milliseconds)
    val progress: Double = 0.0,
    val secondaryProgress: Double = 0.0,
    val status: String = "PLANNING",
    val scoreRaw: Int? = null,
    val repeat: Int = 0,
    val isPrivate: Boolean = false,
    val notes: String = ""
) {
    val updatedAt: Long get() = localUpdatedAt

    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(jsonString: String?): SyncSnapshot? {
            if (jsonString.isNullOrBlank() || jsonString.trim() == "{}") return null
            return runCatching { json.decodeFromString<SyncSnapshot>(jsonString) }.getOrNull()
        }
    }
}

/**
 * Local domain tracking state extracted from [MediaItemEntity].
 */
data class LocalState(
    val id: String = "",
    val userStatus: String = "PLANNING",
    val progress: Double = 0.0,
    val secondaryProgress: Double = 0.0,
    val scoreRaw: Int? = null,
    val repeat: Int = 0,
    val isPrivate: Boolean = false,
    val notes: String = "",
    val updatedAt: Long = 0L // Epoch milliseconds
) {
    companion object {
        fun from(entity: MediaItemEntity): LocalState = LocalState(
            id = entity.id,
            userStatus = entity.userStatus,
            progress = entity.progress,
            secondaryProgress = entity.secondaryProgress,
            scoreRaw = entity.scoreRaw,
            repeat = entity.repeat,
            isPrivate = entity.isPrivate,
            notes = entity.notes,
            updatedAt = entity.updatedAt
        )
    }
}

/**
 * Remote tracking state extracted from [AniListMediaListEntry].
 */
data class RemoteState(
    val remoteId: Int = 0,      // AniList MediaListEntry ID
    val mediaId: Int = 0,       // AniList Media ID
    val status: String? = null, // AniList MediaListStatus (CURRENT, PLANNING, etc.)
    val progress: Int? = null,
    val progressVolumes: Int? = null,
    val scoreRaw: Int? = null,
    val repeat: Int? = null,
    val isPrivate: Boolean? = null,
    val notes: String? = null,
    val updatedAt: Long? = null // AniList epoch seconds
) {
    companion object {
        fun from(entry: AniListMediaListEntry): RemoteState = RemoteState(
            remoteId = entry.id,
            mediaId = entry.resolvedMediaId ?: 0,
            status = entry.status,
            progress = entry.progress,
            progressVolumes = entry.progressVolumes,
            scoreRaw = entry.scoreRaw,
            repeat = entry.repeat,
            isPrivate = entry.isPrivate,
            notes = entry.notes,
            updatedAt = entry.updatedAt
        )
    }
}

enum class ResolutionType {
    IN_SYNC,
    CLEAN_REMOTE,
    CLEAN_LOCAL,
    CONCURRENT
}

data class MergeResult(
    val resolutionType: ResolutionType,
    val syncStatus: String, // "SYNCED" or "DIRTY_LOCAL"
    val mergedState: LocalState,
    val newSnapshot: SyncSnapshot
)

/**
 * Pure domain 3-way merge conflict resolver comparing local, remote, and syncedSnapshot states.
 * Adheres strictly to Section 6.3 of the foundation specification:
 * - Clean remote edit: remote.updatedAt > snapshot.updatedAt && local.updatedAt <= snapshot.updatedAt -> Fast-forward local.
 * - Clean local edit: local.updatedAt > snapshot.updatedAt && remote.updatedAt <= snapshot.updatedAt -> DIRTY_LOCAL.
 * - Concurrent edit: monotonic progress merge (max), status/score resolved via timestamp (local.updatedAt > remote.updatedAt * 1000).
 * - Fractional chapter invariance: preserves local fractional progress if remote integer matches.
 * - In-flight mutation fence: ignores stale remote echoes if local.updatedAt > dispatchedAt.
 */
object ThreeWayMergeResolver {

    const val SYNC_STATUS_SYNCED = "SYNCED"
    const val SYNC_STATUS_DIRTY_LOCAL = "DIRTY_LOCAL"

    private fun normalizeToMillis(epoch: Long): Long =
        if (epoch > 100_000_000_000L) epoch else epoch * 1000L

    private fun normalizeToSeconds(epoch: Long): Long =
        if (epoch > 100_000_000_000L) epoch / 1000L else epoch

    fun anilistStatusToLazyDex(anilistStatus: String?): String {
        return when (anilistStatus?.uppercase()) {
            "CURRENT", "REPEATING" -> "IN_PROGRESS"
            "PLANNING" -> "PLANNING"
            "COMPLETED" -> "COMPLETED"
            "PAUSED" -> "ON_HOLD"
            "DROPPED" -> "DROPPED"
            else -> "PLANNING"
        }
    }

    fun lazyDexStatusToAniList(lazyDexStatus: String, repeat: Int = 0): String {
        return when (lazyDexStatus.uppercase()) {
            "IN_PROGRESS", "READING", "WATCHING", "PLAYING" -> {
                if (repeat > 0) "REPEATING" else "CURRENT"
            }
            "PLANNING", "PLAN_TO" -> "PLANNING"
            "COMPLETED" -> "COMPLETED"
            "ON_HOLD" -> "PAUSED"
            "DROPPED" -> "DROPPED"
            else -> "PLANNING"
        }
    }

    /**
     * Resolves differences between [local], [remote], and the previous [snapshot].
     *
     * @param local The current local media item state.
     * @param remote The incoming AniList tracker state.
     * @param snapshot The state saved at last synchronization, or null if first sync / newly linked.
     * @param dispatchedAt Timestamp (ms) when an in-flight mutation was dispatched, if any.
     */
    fun resolve(
        local: LocalState,
        remote: RemoteState,
        snapshot: SyncSnapshot?,
        dispatchedAt: Long? = null
    ): MergeResult {
        // In-flight mutation fence: if a mutation was dispatched at dispatchedAt and
        // local was modified after dispatchedAt, local strictly wins over any remote echo.
        val fenceActive = dispatchedAt != null && local.updatedAt > dispatchedAt

        if (fenceActive) {
            return resolveCleanLocal(local, remote, snapshot)
        }

        val remoteSec = remote.updatedAt?.let { normalizeToSeconds(it) } ?: 0L
        val snapRemoteSec = snapshot?.remoteUpdatedAt?.let { normalizeToSeconds(it) } ?: 0L
        val snapLocalMs = snapshot?.localUpdatedAt ?: 0L

        // Did remote change relative to snapshot?
        val remoteChanged = if (snapshot == null) {
            remoteSec > 0L
        } else {
            remoteSec > snapRemoteSec
        }

        // Did local change relative to snapshot?
        val localChanged = if (snapshot == null) {
            local.updatedAt > 0L
        } else {
            local.updatedAt > snapLocalMs
        }

        return when {
            // 1. Neither changed: IN_SYNC
            !remoteChanged && !localChanged && snapshot != null -> {
                MergeResult(
                    resolutionType = ResolutionType.IN_SYNC,
                    syncStatus = SYNC_STATUS_SYNCED,
                    mergedState = local,
                    newSnapshot = snapshot
                )
            }

            // 2. Clean Remote Edit: remote changed, local did not
            remoteChanged && !localChanged -> {
                resolveCleanRemote(local, remote, snapshot)
            }

            // 3. Clean Local Edit: local changed, remote did not
            localChanged && !remoteChanged -> {
                resolveCleanLocal(local, remote, snapshot)
            }

            // 4. Concurrent Edit (or initial merge with snapshot == null): both changed
            else -> {
                resolveConcurrent(local, remote, snapshot)
            }
        }
    }

    private fun resolveCleanRemote(
        local: LocalState,
        remote: RemoteState,
        snapshot: SyncSnapshot?
    ): MergeResult {
        // Fractional chapter retention: if remote integer equals local integer, preserve local fraction
        val resolvedProgress = if (remote.progress != null && local.progress.toInt() == remote.progress) {
            local.progress
        } else {
            remote.progress?.toDouble() ?: local.progress
        }

        val resolvedSecondaryProgress = remote.progressVolumes?.toDouble() ?: local.secondaryProgress
        val resolvedStatus = remote.status?.let { anilistStatusToLazyDex(it) } ?: local.userStatus
        val resolvedScore = if (remote.scoreRaw == 0) null else (remote.scoreRaw ?: local.scoreRaw)
        val resolvedRepeat = remote.repeat ?: local.repeat
        val resolvedPrivate = remote.isPrivate ?: local.isPrivate
        val resolvedNotes = remote.notes ?: local.notes
        val remoteMs = remote.updatedAt?.let { normalizeToMillis(it) } ?: local.updatedAt
        val resolvedUpdatedAt = maxOf(local.updatedAt, remoteMs)

        val merged = local.copy(
            userStatus = resolvedStatus,
            progress = resolvedProgress,
            secondaryProgress = resolvedSecondaryProgress,
            scoreRaw = resolvedScore,
            repeat = resolvedRepeat,
            isPrivate = resolvedPrivate,
            notes = resolvedNotes,
            updatedAt = resolvedUpdatedAt
        )

        val newSnapshot = SyncSnapshot(
            remoteUpdatedAt = remote.updatedAt?.let { normalizeToSeconds(it) } ?: 0L,
            localUpdatedAt = merged.updatedAt,
            progress = merged.progress,
            secondaryProgress = merged.secondaryProgress,
            status = merged.userStatus,
            scoreRaw = merged.scoreRaw,
            repeat = merged.repeat,
            isPrivate = merged.isPrivate,
            notes = merged.notes
        )

        return MergeResult(
            resolutionType = ResolutionType.CLEAN_REMOTE,
            syncStatus = SYNC_STATUS_SYNCED,
            mergedState = merged,
            newSnapshot = newSnapshot
        )
    }

    private fun resolveCleanLocal(
        local: LocalState,
        remote: RemoteState,
        snapshot: SyncSnapshot?
    ): MergeResult {
        return MergeResult(
            resolutionType = ResolutionType.CLEAN_LOCAL,
            syncStatus = SYNC_STATUS_DIRTY_LOCAL,
            mergedState = local,
            newSnapshot = snapshot ?: SyncSnapshot(
                remoteUpdatedAt = remote.updatedAt?.let { normalizeToSeconds(it) } ?: 0L,
                localUpdatedAt = local.updatedAt,
                progress = local.progress,
                secondaryProgress = local.secondaryProgress,
                status = local.userStatus,
                scoreRaw = local.scoreRaw,
                repeat = local.repeat,
                isPrivate = local.isPrivate,
                notes = local.notes
            )
        )
    }

    private fun resolveConcurrent(
        local: LocalState,
        remote: RemoteState,
        snapshot: SyncSnapshot?
    ): MergeResult {
        // Monotonic progress merge:
        // Fractional chapter retention: if remote integer equals local integer, preserve local fraction
        val remoteProg = remote.progress?.toDouble() ?: 0.0
        val resolvedProgress = when {
            remote.progress != null && local.progress.toInt() == remote.progress -> local.progress
            remoteProg > local.progress -> remoteProg
            else -> local.progress
        }

        // Secondary progress (volumes)
        val remoteVolumes = (remote.progressVolumes ?: 0).toDouble()
        val resolvedSecondaryProgress = maxOf(local.secondaryProgress, remoteVolumes)

        // Status & Score & other metadata:
        // local wins if local.updatedAt > remote.updatedAt * 1000; otherwise remote wins
        val localMs = local.updatedAt
        val remoteMs = remote.updatedAt?.let { normalizeToMillis(it) } ?: 0L
        val localWins = localMs > remoteMs

        val resolvedStatus = if (localWins) {
            local.userStatus
        } else {
            remote.status?.let { anilistStatusToLazyDex(it) } ?: local.userStatus
        }

        val resolvedScore = if (localWins) {
            local.scoreRaw
        } else {
            if (remote.scoreRaw == 0) null else (remote.scoreRaw ?: local.scoreRaw)
        }

        val resolvedRepeat = if (localWins) local.repeat else (remote.repeat ?: local.repeat)
        val resolvedPrivate = if (localWins) local.isPrivate else (remote.isPrivate ?: local.isPrivate)
        val resolvedNotes = if (localWins) local.notes else (remote.notes ?: local.notes)
        val resolvedUpdatedAt = maxOf(localMs, remoteMs)

        val merged = local.copy(
            userStatus = resolvedStatus,
            progress = resolvedProgress,
            secondaryProgress = resolvedSecondaryProgress,
            scoreRaw = resolvedScore,
            repeat = resolvedRepeat,
            isPrivate = resolvedPrivate,
            notes = resolvedNotes,
            updatedAt = resolvedUpdatedAt
        )

        // Determine if merged state requires outbound push to AniList:
        // If local won status/score/repeat/isPrivate/notes, or if progress/secondaryProgress
        // advanced beyond remote's integer counters, remote needs to be updated -> DIRTY_LOCAL.
        val remoteProgInt = remote.progress ?: 0
        val remoteVolInt = remote.progressVolumes ?: 0
        val needsPush = localWins ||
                merged.progress.toInt() > remoteProgInt ||
                merged.secondaryProgress.toInt() > remoteVolInt

        val syncStatus = if (needsPush) SYNC_STATUS_DIRTY_LOCAL else SYNC_STATUS_SYNCED

        val newSnapshot = SyncSnapshot(
            remoteUpdatedAt = remote.updatedAt?.let { normalizeToSeconds(it) } ?: 0L,
            localUpdatedAt = merged.updatedAt,
            progress = merged.progress,
            secondaryProgress = merged.secondaryProgress,
            status = merged.userStatus,
            scoreRaw = merged.scoreRaw,
            repeat = merged.repeat,
            isPrivate = merged.isPrivate,
            notes = merged.notes
        )

        return MergeResult(
            resolutionType = ResolutionType.CONCURRENT,
            syncStatus = syncStatus,
            mergedState = merged,
            newSnapshot = newSnapshot
        )
    }
}

