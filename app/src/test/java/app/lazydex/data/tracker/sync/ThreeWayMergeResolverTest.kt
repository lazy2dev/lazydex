package app.lazydex.data.tracker.sync

import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.UserDataEntity
import app.lazydex.data.tracker.anilist.dto.AniListMedia
import app.lazydex.data.tracker.anilist.dto.AniListMediaListEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeWayMergeResolverTest {

    private val baseSnapshot = SyncSnapshot(
        remoteUpdatedAt = 1700000000L,       // seconds
        localUpdatedAt = 1700000000000L,     // ms
        progress = 10.0,
        secondaryProgress = 1.0,
        status = "CURRENT",
        scoreRaw = 80,
        repeat = 0,
        isPrivate = false,
        notes = "Original notes"
    )

    private val baseLocal = LocalState(
        id = "local-123",
        userStatus = "CURRENT",
        progress = 10.0,
        secondaryProgress = 1.0,
        scoreRaw = 80,
        repeat = 0,
        isPrivate = false,
        notes = "Original notes",
        updatedAt = 1700000000000L
    )

    private val baseRemote = RemoteState(
        remoteId = 999,
        mediaId = 555,
        status = "CURRENT",
        progress = 10,
        progressVolumes = 1,
        scoreRaw = 80,
        repeat = 0,
        isPrivate = false,
        notes = "Original notes",
        updatedAt = 1700000000L
    )

    @Test
    fun `neither changed results in IN_SYNC and SYNCED status`() {
        val result = ThreeWayMergeResolver.resolve(
            local = baseLocal,
            remote = baseRemote,
            snapshot = baseSnapshot
        )

        assertEquals(ResolutionType.IN_SYNC, result.resolutionType)
        assertEquals(ThreeWayMergeResolver.SYNC_STATUS_SYNCED, result.syncStatus)
        assertEquals(10.0, result.mergedState.progress, 0.001)
        assertEquals(baseSnapshot, result.newSnapshot)
    }

    @Test
    fun `clean remote edit fast-forwards local to remote`() {
        val remoteEdited = baseRemote.copy(
            status = "COMPLETED",
            progress = 24,
            progressVolumes = 2,
            scoreRaw = 95,
            repeat = 1,
            notes = "Updated on web",
            updatedAt = 1700000500L // Newer than snapshot
        )

        val result = ThreeWayMergeResolver.resolve(
            local = baseLocal, // Unchanged
            remote = remoteEdited,
            snapshot = baseSnapshot
        )

        assertEquals(ResolutionType.CLEAN_REMOTE, result.resolutionType)
        assertEquals(ThreeWayMergeResolver.SYNC_STATUS_SYNCED, result.syncStatus)
        assertEquals(24.0, result.mergedState.progress, 0.001)
        assertEquals(2.0, result.mergedState.secondaryProgress, 0.001)
        assertEquals("COMPLETED", result.mergedState.userStatus)
        assertEquals(95, result.mergedState.scoreRaw)
        assertEquals(1, result.mergedState.repeat)
        assertEquals("Updated on web", result.mergedState.notes)
        assertEquals(1700000500L, result.newSnapshot.remoteUpdatedAt)
    }

    @Test
    fun `clean remote edit preserves fractional chapter when integer matches`() {
        // Local has read chapter 12.5 (snapshot recorded 12.5)
        val fractionalSnapshot = baseSnapshot.copy(progress = 12.5)
        val fractionalLocal = baseLocal.copy(progress = 12.5)

        // AniList returns integer 12, but user edited score on web so remoteUpdatedAt increased
        val remoteScoreEdit = baseRemote.copy(
            progress = 12,
            scoreRaw = 90,
            updatedAt = 1700000500L
        )

        val result = ThreeWayMergeResolver.resolve(
            local = fractionalLocal,
            remote = remoteScoreEdit,
            snapshot = fractionalSnapshot
        )

        assertEquals(ResolutionType.CLEAN_REMOTE, result.resolutionType)
        assertEquals(ThreeWayMergeResolver.SYNC_STATUS_SYNCED, result.syncStatus)
        // Fractional 12.5 preserved because remote.progress == local.progress.toInt()
        assertEquals(12.5, result.mergedState.progress, 0.001)
        assertEquals(90, result.mergedState.scoreRaw)
    }

    @Test
    fun `clean remote edit advances progress when remote integer is higher`() {
        val fractionalSnapshot = baseSnapshot.copy(progress = 12.5)
        val fractionalLocal = baseLocal.copy(progress = 12.5)

        // Remote read ahead to chapter 15
        val remoteAdvanced = baseRemote.copy(
            progress = 15,
            updatedAt = 1700000500L
        )

        val result = ThreeWayMergeResolver.resolve(
            local = fractionalLocal,
            remote = remoteAdvanced,
            snapshot = fractionalSnapshot
        )

        assertEquals(ResolutionType.CLEAN_REMOTE, result.resolutionType)
        assertEquals(15.0, result.mergedState.progress, 0.001)
    }

    @Test
    fun `clean local edit keeps local state and marks DIRTY_LOCAL`() {
        val localEdited = baseLocal.copy(
            progress = 11.0,
            updatedAt = 1700000600000L // Local tap
        )

        val result = ThreeWayMergeResolver.resolve(
            local = localEdited,
            remote = baseRemote, // Remote unchanged
            snapshot = baseSnapshot
        )

        assertEquals(ResolutionType.CLEAN_LOCAL, result.resolutionType)
        assertEquals(ThreeWayMergeResolver.SYNC_STATUS_DIRTY_LOCAL, result.syncStatus)
        assertEquals(11.0, result.mergedState.progress, 0.001)
        assertEquals("CURRENT", result.mergedState.userStatus)
        assertEquals(baseSnapshot, result.newSnapshot)
    }

    @Test
    fun `concurrent edit monotonic progress merge - local progress higher`() {
        val localEdited = baseLocal.copy(
            progress = 25.0,
            updatedAt = 1700000700000L
        )
        val remoteEdited = baseRemote.copy(
            progress = 20,
            updatedAt = 1700000600L
        )

        val result = ThreeWayMergeResolver.resolve(
            local = localEdited,
            remote = remoteEdited,
            snapshot = baseSnapshot
        )

        assertEquals(ResolutionType.CONCURRENT, result.resolutionType)
        // Monotonic: max(25, 20) = 25
        assertEquals(25.0, result.mergedState.progress, 0.001)
        // Local is higher than remote, so remote needs push
        assertEquals(ThreeWayMergeResolver.SYNC_STATUS_DIRTY_LOCAL, result.syncStatus)
    }

    @Test
    fun `concurrent edit monotonic progress merge - remote progress higher`() {
        val localEdited = baseLocal.copy(
            progress = 20.0,
            updatedAt = 1700000600000L
        )
        val remoteEdited = baseRemote.copy(
            progress = 28,
            updatedAt = 1700000700L
        )

        val result = ThreeWayMergeResolver.resolve(
            local = localEdited,
            remote = remoteEdited,
            snapshot = baseSnapshot
        )

        assertEquals(ResolutionType.CONCURRENT, result.resolutionType)
        // Monotonic: max(20, 28) = 28
        assertEquals(28.0, result.mergedState.progress, 0.001)
    }

    @Test
    fun `concurrent edit preserves fractional chapter when integer matches`() {
        val localEdited = baseLocal.copy(
            progress = 14.5,
            updatedAt = 1700000600000L
        )
        val remoteEdited = baseRemote.copy(
            progress = 14,
            scoreRaw = 88,
            updatedAt = 1700000700L
        )

        val result = ThreeWayMergeResolver.resolve(
            local = localEdited,
            remote = remoteEdited,
            snapshot = baseSnapshot
        )

        assertEquals(ResolutionType.CONCURRENT, result.resolutionType)
        assertEquals(14.5, result.mergedState.progress, 0.001)
    }

    @Test
    fun `concurrent edit secondary progress monotonic max`() {
        val localEdited = baseLocal.copy(
            secondaryProgress = 3.0,
            updatedAt = 1700000600000L
        )
        val remoteEdited = baseRemote.copy(
            progressVolumes = 5,
            updatedAt = 1700000700L
        )

        val result = ThreeWayMergeResolver.resolve(
            local = localEdited,
            remote = remoteEdited,
            snapshot = baseSnapshot
        )

        assertEquals(ResolutionType.CONCURRENT, result.resolutionType)
        assertEquals(5.0, result.mergedState.secondaryProgress, 0.001)
    }

    @Test
    fun `concurrent edit status and score - local newer wins`() {
        // Local modified at 1700000800000 ms
        val localEdited = baseLocal.copy(
            userStatus = "COMPLETED",
            scoreRaw = 92,
            updatedAt = 1700000800000L
        )
        // Remote modified at 1700000600 s (1700000600000 ms)
        val remoteEdited = baseRemote.copy(
            status = "PAUSED",
            scoreRaw = 60,
            updatedAt = 1700000600L
        )

        val result = ThreeWayMergeResolver.resolve(
            local = localEdited,
            remote = remoteEdited,
            snapshot = baseSnapshot
        )

        assertEquals(ResolutionType.CONCURRENT, result.resolutionType)
        // Local wins because local.updatedAt > remote.updatedAt * 1000
        assertEquals("COMPLETED", result.mergedState.userStatus)
        assertEquals(92, result.mergedState.scoreRaw)
        assertEquals(ThreeWayMergeResolver.SYNC_STATUS_DIRTY_LOCAL, result.syncStatus)
    }

    @Test
    fun `concurrent edit status and score - remote newer wins`() {
        // Local modified at 1700000500000 ms
        val localEdited = baseLocal.copy(
            userStatus = "PLANNING",
            scoreRaw = 50,
            updatedAt = 1700000500000L
        )
        // Remote modified at 1700000900 s (1700000900000 ms)
        val remoteEdited = baseRemote.copy(
            status = "CURRENT",
            scoreRaw = 85,
            updatedAt = 1700000900L
        )

        val result = ThreeWayMergeResolver.resolve(
            local = localEdited,
            remote = remoteEdited,
            snapshot = baseSnapshot
        )

        assertEquals(ResolutionType.CONCURRENT, result.resolutionType)
        // Remote wins because remote.updatedAt * 1000 >= local.updatedAt
        assertEquals("CURRENT", result.mergedState.userStatus)
        assertEquals(85, result.mergedState.scoreRaw)
    }

    @Test
    fun `in-flight mutation fence protects local tap from stale remote echo`() {
        // Mutation dispatched at t = 1000
        val dispatchTime = 1700000500000L

        // User taps progress +1 after dispatch (t = 1050)
        val localTappedAfter = baseLocal.copy(
            progress = 11.0,
            updatedAt = 1700000550000L
        )

        // Delayed network echo arrives reporting old progress 10 and updatedAt = 1000
        val remoteStaleEcho = baseRemote.copy(
            progress = 10,
            updatedAt = 1700000500L
        )

        val result = ThreeWayMergeResolver.resolve(
            local = localTappedAfter,
            remote = remoteStaleEcho,
            snapshot = baseSnapshot,
            dispatchedAt = dispatchTime
        )

        // Local tap is preserved and marked DIRTY_LOCAL to be dispatched
        assertEquals(ResolutionType.CLEAN_LOCAL, result.resolutionType)
        assertEquals(ThreeWayMergeResolver.SYNC_STATUS_DIRTY_LOCAL, result.syncStatus)
        assertEquals(11.0, result.mergedState.progress, 0.001)
    }

    @Test
    fun `null snapshot initial sync - remote newer adopts remote cleanly`() {
        val initialLocal = baseLocal.copy(updatedAt = 0L)
        val activeRemote = baseRemote.copy(
            progress = 42,
            status = "CURRENT",
            updatedAt = 1700000500L
        )

        val result = ThreeWayMergeResolver.resolve(
            local = initialLocal,
            remote = activeRemote,
            snapshot = null
        )

        assertEquals(42.0, result.mergedState.progress, 0.001)
        assertEquals("CURRENT", result.mergedState.userStatus)
        assertEquals(ThreeWayMergeResolver.SYNC_STATUS_SYNCED, result.syncStatus)
    }

    @Test
    fun `null snapshot initial sync - local newer marks DIRTY_LOCAL`() {
        val localWithHistory = baseLocal.copy(
            progress = 15.0,
            updatedAt = 1700000800000L
        )
        val initialRemote = baseRemote.copy(
            progress = 0,
            status = "PLANNING",
            updatedAt = 1700000100L
        )

        val result = ThreeWayMergeResolver.resolve(
            local = localWithHistory,
            remote = initialRemote,
            snapshot = null
        )

        assertEquals(ResolutionType.CONCURRENT, result.resolutionType)
        assertEquals(15.0, result.mergedState.progress, 0.001)
        assertEquals("CURRENT", result.mergedState.userStatus)
        assertEquals(ThreeWayMergeResolver.SYNC_STATUS_DIRTY_LOCAL, result.syncStatus)
    }

    @Test
    fun `remote score 0 is interpreted as unrated null`() {
        val remoteUnrated = baseRemote.copy(
            scoreRaw = 0,
            updatedAt = 1700000500L
        )

        val result = ThreeWayMergeResolver.resolve(
            local = baseLocal,
            remote = remoteUnrated,
            snapshot = baseSnapshot
        )

        assertNull(result.mergedState.scoreRaw)
    }

    @Test
    fun `status mapping bidirectional translations`() {
        assertEquals("CURRENT", ThreeWayMergeResolver.anilistStatusToLazyDex("CURRENT"))
        assertEquals("REPEATING", ThreeWayMergeResolver.anilistStatusToLazyDex("REPEATING"))
        assertEquals("PLANNING", ThreeWayMergeResolver.anilistStatusToLazyDex("PLANNING"))
        assertEquals("COMPLETED", ThreeWayMergeResolver.anilistStatusToLazyDex("COMPLETED"))
        assertEquals("PAUSED", ThreeWayMergeResolver.anilistStatusToLazyDex("PAUSED"))
        assertEquals("DROPPED", ThreeWayMergeResolver.anilistStatusToLazyDex("DROPPED"))
        assertEquals("PLANNING", ThreeWayMergeResolver.anilistStatusToLazyDex(null))

        assertEquals("CURRENT", ThreeWayMergeResolver.lazyDexStatusToAniList("CURRENT"))
        assertEquals("REPEATING", ThreeWayMergeResolver.lazyDexStatusToAniList("REPEATING"))
        assertEquals("CURRENT", ThreeWayMergeResolver.lazyDexStatusToAniList("IN_PROGRESS", repeat = 0))
        assertEquals("REPEATING", ThreeWayMergeResolver.lazyDexStatusToAniList("IN_PROGRESS", repeat = 2))
        assertEquals("CURRENT", ThreeWayMergeResolver.lazyDexStatusToAniList("READING", repeat = 0))
        assertEquals("CURRENT", ThreeWayMergeResolver.lazyDexStatusToAniList("WATCHING", repeat = 0))
        assertEquals("CURRENT", ThreeWayMergeResolver.lazyDexStatusToAniList("PLAYING", repeat = 0))
        assertEquals("PLANNING", ThreeWayMergeResolver.lazyDexStatusToAniList("PLANNING"))
        assertEquals("PLANNING", ThreeWayMergeResolver.lazyDexStatusToAniList("PLAN_TO"))
        assertEquals("COMPLETED", ThreeWayMergeResolver.lazyDexStatusToAniList("COMPLETED"))
        assertEquals("PAUSED", ThreeWayMergeResolver.lazyDexStatusToAniList("PAUSED"))
        assertEquals("PAUSED", ThreeWayMergeResolver.lazyDexStatusToAniList("ON_HOLD"))
        assertEquals("DROPPED", ThreeWayMergeResolver.lazyDexStatusToAniList("DROPPED"))
    }

    @Test
    fun `snapshot json serialization and deserialization roundtrip`() {
        val snapshot = SyncSnapshot(
            remoteUpdatedAt = 1700000123L,
            localUpdatedAt = 1700000123456L,
            progress = 33.5,
            secondaryProgress = 4.0,
            status = "COMPLETED",
            scoreRaw = 88,
            repeat = 1,
            isPrivate = true,
            notes = "Test notes"
        )

        val jsonString = snapshot.toJson()
        assertTrue(jsonString.contains("\"remoteUpdatedAt\":1700000123"))
        assertTrue(jsonString.contains("\"progress\":33.5"))

        val restored = SyncSnapshot.fromJson(jsonString)
        assertNotNull(restored)
        assertEquals(snapshot, restored)

        assertNull(SyncSnapshot.fromJson(null))
        assertNull(SyncSnapshot.fromJson(""))
        assertNull(SyncSnapshot.fromJson("{}"))
        assertNull(SyncSnapshot.fromJson("invalid json"))
    }

    @Test
    fun `from Entity and DTO factories extract expected properties`() {
        val entity = MediaItemEntity(
            id = "media-1",
            type = "MANGA",
            format = "MANGA",
            title = "Test Manga",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        val userData = UserDataEntity(
            mediaId = "media-1",
            userStatus = "COMPLETED",
            progress = 55.0,
            secondaryProgress = 5.0,
            scoreRaw = 90,
            repeat = 1,
            notes = "Great read",
            isPrivate = false,
            updatedAt = 2000L
        )
        val localState = LocalState.from(entity, userData)
        assertEquals("media-1", localState.id)
        assertEquals("COMPLETED", localState.userStatus)
        assertEquals(55.0, localState.progress, 0.001)
        assertEquals(5.0, localState.secondaryProgress, 0.001)
        assertEquals(90, localState.scoreRaw)
        assertEquals(1, localState.repeat)
        assertEquals("Great read", localState.notes)
        assertEquals(2000L, localState.updatedAt)

        val dto = AniListMediaListEntry(
            id = 777,
            mediaId = 888,
            status = "PAUSED",
            scoreRaw = 75,
            progress = 12,
            progressVolumes = 2,
            repeat = 0,
            isPrivate = true,
            notes = "Paused reading",
            updatedAt = 1600000L,
            media = AniListMedia(id = 888)
        )
        val remoteState = RemoteState.from(dto)
        assertEquals(777, remoteState.remoteId)
        assertEquals(888, remoteState.mediaId)
        assertEquals("PAUSED", remoteState.status)
        assertEquals(75, remoteState.scoreRaw)
        assertEquals(12, remoteState.progress)
        assertEquals(2, remoteState.progressVolumes)
        assertEquals(0, remoteState.repeat)
        assertEquals(true, remoteState.isPrivate)
        assertEquals("Paused reading", remoteState.notes)
        assertEquals(1600000L, remoteState.updatedAt)
    }

    @Test
    fun `monotonic progress prevents lower remote progress from overwriting higher local progress`() {
        val higherLocal = baseLocal.copy(progress = 45.0, updatedAt = 1700000100_000L)
        val lowerRemote = baseRemote.copy(progress = 30, updatedAt = 1700000500L) // remote updated later but has lower progress

        val result = ThreeWayMergeResolver.resolve(
            local = higherLocal,
            remote = lowerRemote,
            snapshot = baseSnapshot
        )

        assertEquals(45.0, result.mergedState.progress, 0.001)
    }

    @Test
    fun `reread with higher repeat count allows progress to reset to lower number`() {
        val localFinished = baseLocal.copy(progress = 100.0, repeat = 0, updatedAt = 1700000100_000L)
        val remoteRereading = baseRemote.copy(progress = 5, repeat = 1, status = "CURRENT", updatedAt = 1700000500L)

        val result = ThreeWayMergeResolver.resolve(
            local = localFinished,
            remote = remoteRereading,
            snapshot = baseSnapshot
        )

        assertEquals(5.0, result.mergedState.progress, 0.001)
        assertEquals(1, result.mergedState.repeat)
    }
}

