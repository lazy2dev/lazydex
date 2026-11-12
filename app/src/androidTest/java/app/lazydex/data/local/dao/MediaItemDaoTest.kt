package app.lazydex.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lazydex.data.local.LazyDexDatabase
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.domain.model.MediaStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MediaItemDaoTest {

    private lateinit var db: LazyDexDatabase
    private lateinit var dao: MediaItemDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LazyDexDatabase::class.java).build()
        dao = db.mediaItemDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testGetStatsCalculations() = runBlocking {
        // Insert sample items:
        // Item 1: Novel, completed, progress = 10, rating = 5.0
        val item1 = MediaItemEntity(
            id = "1",
            type = "NOVEL",
            title = "Novel 1",
            alternativeTitles = "[]",
            sourceUrl = "url1",
            coverImagePath = "",
            coverImageUrl = null,
            currentProgress = 10,
            totalItems = 10,
            userStatus = "COMPLETED",
            rating = 5.0,
            notes = "",
            lastUpdated = 0L,
            dateAdded = 100L
        )
        // Item 2: Manga, reading, progress = 5, rating = 4.0
        val item2 = MediaItemEntity(
            id = "2",
            type = "MANGA",
            title = "Manga 1",
            alternativeTitles = "[]",
            sourceUrl = "url2",
            coverImagePath = "",
            coverImageUrl = null,
            currentProgress = 5,
            totalItems = 20,
            userStatus = "READING",
            rating = 4.0,
            notes = "",
            lastUpdated = 0L,
            dateAdded = 200L
        )
        // Item 3: Anime, watching, progress = 12, rating = null
        val item3 = MediaItemEntity(
            id = "3",
            type = "ANIME",
            title = "Anime 1",
            alternativeTitles = "[]",
            sourceUrl = "url3",
            coverImagePath = "",
            coverImageUrl = null,
            currentProgress = 12,
            totalItems = 12,
            userStatus = "WATCHING",
            rating = null,
            notes = "",
            lastUpdated = 0L,
            dateAdded = 300L
        )
        
        dao.upsert(item1)
        dao.upsert(item2)
        dao.upsert(item3)

        // Retrieve pre-computed stats
        val stats: MediaStats = dao.getStats().first()

        assertEquals(3, stats.totalCount)
        assertEquals(1, stats.completedCount)
        assertEquals(27, stats.totalProgress)
        // Mean rating of 5.0 and 4.0 is 4.5
        assertEquals(4.5, stats.meanRating ?: 0.0, 0.001)
        assertEquals(2, stats.inProgressCount) // reading and watching
        assertEquals(1, stats.novelCount)
        assertEquals(1, stats.mangaCount)
        assertEquals(1, stats.animeCount)
        assertEquals(0, stats.gameCount)
    }
}
