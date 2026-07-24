package app.lazydex.data.anilist

import app.lazydex.data.local.dao.MediaItemDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnilistSyncManagerTest {

    private val api: AnilistApi = mockk(relaxed = true)
    private val tokenStore: AnilistTokenStore = mockk(relaxed = true)
    private val dao: MediaItemDao = mockk(relaxed = true)

    private lateinit var syncManager: AnilistSyncManager

    @Before
    fun setup() {
        syncManager = AnilistSyncManager(api, tokenStore, dao)
    }

    @Test
    fun performFullSyncFlow_emitsError_whenNotLoggedIn() = runTest {
        coEvery { tokenStore.isLoggedIn() } returns false

        val states = syncManager.performFullSyncFlow().toList()

        assertTrue(states.first() is SyncProgressState.Fetching)
        val lastState = states.last()
        assertTrue(lastState is SyncProgressState.Error)
        assertEquals("Not logged in to AniList. Please connect your account.", (lastState as SyncProgressState.Error).message)
    }

    @Test
    fun performFullSyncFlow_emitsFetchingAnalyzingSyncingCompleted_whenSuccessful() = runTest {
        coEvery { tokenStore.isLoggedIn() } returns true
        coEvery { tokenStore.getUsername() } returns "testuser"
        coEvery { dao.getAll() } returns emptyList()
        coEvery { dao.getBoundItems() } returns emptyList()

        val sampleMediaList = listOf(
            ALMediaListEntry(
                id = 101L,
                mediaId = 201L,
                status = "CURRENT",
                progress = 5,
                media = ALMedia(
                    id = 201L,
                    title = ALTitle(userPreferred = "Frieren")
                )
            )
        )

        coEvery { api.fetchMediaListPage("testuser", "ANIME", 1) } returns ALPage(ALPageInfo(false), sampleMediaList)
        coEvery { api.fetchMediaListPage("testuser", "MANGA", 1) } returns ALPage(ALPageInfo(false), emptyList())

        val states = syncManager.performFullSyncFlow().toList()

        assertTrue(states.any { it is SyncProgressState.Fetching })
        assertTrue(states.any { it is SyncProgressState.Analyzing })
        assertTrue(states.any { it is SyncProgressState.Syncing })
        val completed = states.last() as SyncProgressState.Completed
        assertEquals(1, completed.report.importedCount)
        assertEquals(0, completed.report.updatedCount)
    }
}
