package app.lazydex.sync

import app.lazydex.data.local.dao.CategoryCount
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.StatusCount
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.sync.InboundHarvester
import app.lazydex.domain.model.MediaStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class InboundHarvesterTest {

    private class FakeMediaItemDao : MediaItemDao {
        val items = mutableListOf<MediaItemEntity>()

        override fun observeAll(): Flow<List<MediaItemEntity>> = emptyFlow()
        override fun observeByCategory(category: String): Flow<List<MediaItemEntity>> = emptyFlow()
        override fun observeCategoryCounts(): Flow<List<CategoryCount>> = emptyFlow()
        override fun observeStatusCounts(category: String?): Flow<List<StatusCount>> = emptyFlow()
        override fun observeFiltered(category: String?, filterType: String, exactStatus: String?): Flow<List<MediaItemEntity>> = emptyFlow()
        override fun observeAllByDateAdded(): Flow<List<MediaItemEntity>> = emptyFlow()
        override fun observeAllByLastUpdated(): Flow<List<MediaItemEntity>> = emptyFlow()
        override fun observeAllByTitle(): Flow<List<MediaItemEntity>> = emptyFlow()
        override fun observeAllByProgress(): Flow<List<MediaItemEntity>> = emptyFlow()
        override fun observeCount(): Flow<Int> = emptyFlow()
        override fun observeById(id: String): Flow<MediaItemEntity?> = emptyFlow()
        override suspend fun getById(id: String): MediaItemEntity? = items.firstOrNull { it.id == id }
        override suspend fun getAll(): List<MediaItemEntity> = items.filter { !it.isDeleted }
        override suspend fun getAllIncludingDeleted(): List<MediaItemEntity> = items
        override suspend fun findByExtraPattern(keyPattern: String): List<MediaItemEntity> = items.filter { it.extraData.contains(keyPattern) }
        override suspend fun existsByUrl(url: String): Boolean = items.any { it.sourceUrl == url }
        override suspend fun upsert(item: MediaItemEntity) {
            items.removeAll { it.id == item.id }
            items.add(item)
        }
        override suspend fun upsertAll(newItems: List<MediaItemEntity>) {
            newItems.forEach { upsert(it) }
        }
        override suspend fun atomicIncrement(id: String, now: Long) {}
        override suspend fun atomicDecrement(id: String, now: Long) {}
        override suspend fun updateStatus(id: String, status: String, now: Long) {}
        override suspend fun softDelete(id: String, now: Long) {}
        override suspend fun deleteById(id: String) { items.removeAll { it.id == id } }
        override suspend fun deleteAll() { items.clear() }
        override fun getStats(): Flow<MediaStats> = emptyFlow()
    }

    @Test
    fun harvest_blankUsername_returnsZero() = runTest {
        val fakeDao = FakeMediaItemDao()
        val harvester = InboundHarvester(fakeDao, OkHttpClient())
        val result = harvester.harvestFromAniList("")
        assertEquals(0, result.addedCount)
        assertEquals(0, result.updatedCount)
    }
}
