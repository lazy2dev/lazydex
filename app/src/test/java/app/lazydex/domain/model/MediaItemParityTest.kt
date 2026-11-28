package app.lazydex.domain.model

import app.lazydex.data.local.dao.ActivityDao
import app.lazydex.data.local.dao.AppSettingDao
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.dao.StatusCount
import app.lazydex.data.local.dao.TypeCount
import app.lazydex.data.local.entity.ActiveMediaItemView
import app.lazydex.data.local.entity.ActivityEntity
import app.lazydex.data.local.entity.AppSettingEntity
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.local.entity.UserDataEntity
import app.lazydex.data.repository.MediaRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MediaItemParityTest {

    @Test
    fun `movie and tv media items have correct typeId, mediaType, unitName, and actionVerb`() {
        val movie = MediaItem(
            id = "movie-1",
            type = "MOVIE",
            format = "MOVIE",
            title = "Inception",
            userStatus = UserStatus.PLANNING,
            lastUpdated = 0L,
            dateAdded = 0L
        )
        assertEquals("typ_movie", movie.typeId)
        assertEquals(MediaType.MOVIE, movie.mediaType)
        assertEquals("Movie", movie.unitName)
        assertEquals("Watch", movie.actionVerb)

        val tv = MediaItem(
            id = "tv-1",
            type = "TV",
            format = "TV",
            title = "Breaking Bad",
            userStatus = UserStatus.CURRENT,
            lastUpdated = 0L,
            dateAdded = 0L
        )
        assertEquals("typ_tv", tv.typeId)
        assertEquals(MediaType.TV, tv.mediaType)
        assertEquals("Episode", tv.unitName)
        assertEquals("Watch", tv.actionVerb)
    }

    @Test
    fun `secondary constructors preserve MOVIE and TV without collapsing to ANIME`() {
        val movie1 = MediaItem(
            id = "m1",
            mediaType = MediaType.MOVIE,
            title = "Oppenheimer",
            userStatus = UserStatus.COMPLETED
        )
        assertEquals("MOVIE", movie1.type)
        assertEquals(MediaType.MOVIE, movie1.mediaType)
        assertEquals("Movie", movie1.unitName)

        val tv1 = MediaItem(
            id = "t1",
            mediaType = MediaType.TV,
            title = "Chernobyl",
            userStatus = UserStatus.COMPLETED
        )
        assertEquals("TV", tv1.type)
        assertEquals(MediaType.TV, tv1.mediaType)
        assertEquals("Episode", tv1.unitName)

        val movie2 = MediaItem(
            id = "m2",
            typeId = "typ_movie",
            title = "Interstellar",
            userStatus = UserStatus.PLANNING,
            mediaType = MediaType.MOVIE
        )
        assertEquals("MOVIE", movie2.type)
        assertEquals(MediaType.MOVIE, movie2.mediaType)

        val tv2 = MediaItem(
            id = "t2",
            typeId = "typ_tv",
            title = "Succession",
            userStatus = UserStatus.CURRENT,
            mediaType = MediaType.TV
        )
        assertEquals("TV", tv2.type)
        assertEquals(MediaType.TV, tv2.mediaType)
    }

    private class StubMediaItemDao : MediaItemDao {
        override fun observeAll(): Flow<List<ActiveMediaItemView>> = emptyFlow()
        override fun observeHistory(): Flow<List<ActiveMediaItemView>> = emptyFlow()
        override fun observeByType(type: String): Flow<List<ActiveMediaItemView>> = emptyFlow()
        override fun observeTypeCounts(): Flow<List<TypeCount>> = emptyFlow()
        override fun observeStatusCounts(type: String?): Flow<List<StatusCount>> = emptyFlow()
        override fun observeFiltered(type: String?, format: String?, filterType: String): Flow<List<ActiveMediaItemView>> = emptyFlow()
        override fun observeFilteredLegacy(type: String?, filterType: String): Flow<List<ActiveMediaItemView>> = emptyFlow()
        override fun observeCount(): Flow<Int> = emptyFlow()
        override fun observeById(id: String): Flow<ActiveMediaItemView?> = emptyFlow()
        override suspend fun getById(id: String): MediaItemEntity? = null
        override suspend fun getActiveById(id: String): ActiveMediaItemView? = null
        override suspend fun getAll(): List<ActiveMediaItemView> = emptyList()
        override suspend fun getActiveByType(type: String): List<MediaItemEntity> = emptyList()
        override suspend fun upsert(item: MediaItemEntity) {}
        override suspend fun upsertAll(items: List<MediaItemEntity>) {}
        override suspend fun getUserData(mediaId: String): UserDataEntity? = null
        override suspend fun getAllUserData(): List<UserDataEntity> = emptyList()
        override suspend fun upsertUserData(userData: UserDataEntity) {}
        override suspend fun upsertUserDataAll(items: List<UserDataEntity>) {}
        override suspend fun atomicIncrementProgress(id: String, delta: Double, now: Long): Int = 0
        override suspend fun softDelete(id: String, now: Long) {}
        override suspend fun restore(id: String, now: Long) {}
        override suspend fun hardDelete(id: String) {}
        override suspend fun deleteAll() {}
        override suspend fun updateMetadata(id: String, description: String?, tags: String?, genres: String?): Int = 0
        override fun getStats(): Flow<MediaStats> = emptyFlow()
    }

    private class StubMediaLinkDao : MediaLinkDao {
        override fun observeByMediaId(mediaId: String): Flow<List<MediaLinkEntity>> = emptyFlow()
        override suspend fun getByMediaId(mediaId: String): List<MediaLinkEntity> = emptyList()
        override suspend fun getByRemoteId(providerId: String, remoteId: String): MediaLinkEntity? = null
        override suspend fun getDirtyLinks(providerId: String): List<MediaLinkEntity> = emptyList()
        override suspend fun getByProviderAndState(providerId: String, state: String): List<MediaLinkEntity> = emptyList()
        override suspend fun getByProviderId(providerId: String): List<MediaLinkEntity> = emptyList()
        override suspend fun getByMediaIdAndProvider(mediaId: String, providerId: String): MediaLinkEntity? = null
        override suspend fun getLinksNeedingMetadata(providerId: String, limit: Int): List<MediaLinkEntity> = emptyList()
        override suspend fun markMetadataFetched(providerId: String, remoteIds: List<String>, timestamp: Long): Int = 0
        override suspend fun updateSyncStatusByMediaId(mediaId: String, syncStatus: String, updatedAt: Long): Int = 0
        override suspend fun upsert(link: MediaLinkEntity) {}
        override suspend fun upsertAll(links: List<MediaLinkEntity>) {}
        override suspend fun delete(id: String) {}
    }

    private class StubActivityDao : ActivityDao {
        override fun observeByMediaId(mediaId: String): Flow<List<ActivityEntity>> = emptyFlow()
        override fun observeActivityPoints(startMs: Long?, endMs: Long?): Flow<List<ActivityDatePoint>> = emptyFlow()
        override suspend fun getByMediaId(mediaId: String): List<ActivityEntity> = emptyList()
        override suspend fun findCoalescableProgress(mediaId: String, mode: String?, minTimestamp: Long): ActivityEntity? = null
        override suspend fun getCountedProgressUpdates(mediaId: String): List<ActivityEntity> = emptyList()
        override suspend fun upsert(activity: ActivityEntity) {}
        override suspend fun delete(id: String) {}
    }

    private class StubAppSettingDao : AppSettingDao {
        override suspend fun get(key: String): String? = null
        override suspend fun set(setting: AppSettingEntity) {}
        override suspend fun getAll(): List<AppSettingEntity> = emptyList()
    }

    @Test
    fun `getResolvedConfig returns dedicated configurations for MOVIE and TV`() = runBlocking {
        val repo = MediaRepositoryImpl(
            db = null,
            dao = StubMediaItemDao(),
            linkDao = StubMediaLinkDao(),
            activityDao = StubActivityDao(),
            settingDao = StubAppSettingDao(),
            localCoversDir = File("")
        )

        val movieConfig = repo.getResolvedConfig("MOVIE")
        assertEquals("MOVIE", movieConfig.topLevelId)
        assertEquals("movie", movieConfig.icon)
        assertEquals("Movie", movieConfig.unitName)
        assertEquals("Watch", movieConfig.actionVerb)

        val tvConfig = repo.getResolvedConfig("TV")
        assertEquals("TV", tvConfig.topLevelId)
        assertEquals("tv", tvConfig.icon)
        assertEquals("Episode", tvConfig.unitName)
        assertEquals("Watch", tvConfig.actionVerb)

        val typMovieConfig = repo.getResolvedConfig("typ_movie")
        assertEquals("MOVIE", typMovieConfig.topLevelId)

        val typTvConfig = repo.getResolvedConfig("typ_tv")
        assertEquals("TV", typTvConfig.topLevelId)

        val animeSeriesConfig = repo.getResolvedConfig("TYP_ANIME_SERIES")
        assertEquals("ANIME", animeSeriesConfig.topLevelId)
        assertEquals("TV", animeSeriesConfig.displayName)

        val animeMovieConfig = repo.getResolvedConfig("TYP_ANIME_MOVIE")
        assertEquals("ANIME", animeMovieConfig.topLevelId)
        assertEquals("MOVIE", animeMovieConfig.displayName)
    }
}
