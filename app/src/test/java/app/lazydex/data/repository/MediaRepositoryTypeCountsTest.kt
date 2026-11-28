package app.lazydex.data.repository

import app.lazydex.data.local.dao.ActivityDao
import app.lazydex.data.local.dao.AppSettingDao
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.dao.TypeCount
import app.lazydex.domain.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class MediaRepositoryTypeCountsTest {

    private fun createRepository(countsFlow: MutableStateFlow<List<TypeCount>>): MediaRepositoryImpl {
        val itemDao = Proxy.newProxyInstance(
            MediaItemDao::class.java.classLoader,
            arrayOf(MediaItemDao::class.java)
        ) { _, method, _ ->
            if (method.name == "observeTypeCounts") countsFlow else null
        } as MediaItemDao

        return MediaRepositoryImpl(
            db = null,
            dao = itemDao,
            linkDao = noOpDao(),
            activityDao = noOpDao(),
            settingDao = noOpDao(),
            localCoversDir = File("")
        )
    }

    private inline fun <reified T> noOpDao(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java)
    ) { _, _, _ -> null } as T

    @Test
    fun `observeTypeCounts resolves all six media types without overwriting NOVEL`() = runTest {
        val countsFlow = MutableStateFlow(
            listOf(
                TypeCount(type = "NOVEL", count = 10),
                TypeCount(type = "MANGA", count = 7),
                TypeCount(type = "ANIME", count = 5),
                TypeCount(type = "GAME", count = 3),
                TypeCount(type = "MOVIE", count = 2),
                TypeCount(type = "TV", count = 4)
            )
        )
        val repo = createRepository(countsFlow)

        val result = repo.observeTypeCounts().first()

        assertEquals(10, result[MediaType.NOVEL])
        assertEquals(7, result[MediaType.MANGA])
        assertEquals(5, result[MediaType.ANIME])
        assertEquals(3, result[MediaType.GAME])
        assertEquals(2, result[MediaType.MOVIE])
        assertEquals(4, result[MediaType.TV])
    }

    @Test
    fun `observeTypeCounts handles lowercase types and aggregates unknowns into NOVEL`() = runTest {
        val countsFlow = MutableStateFlow(
            listOf(
                TypeCount(type = "NOVEL", count = 6),
                TypeCount(type = "movie", count = 2),
                TypeCount(type = "tv", count = 3),
                TypeCount(type = "UNKNOWN", count = 1)
            )
        )
        val repo = createRepository(countsFlow)

        val result = repo.observeTypeCounts().first()

        assertEquals(7, result[MediaType.NOVEL])
        assertEquals(2, result[MediaType.MOVIE])
        assertEquals(3, result[MediaType.TV])
        assertEquals(0, result[MediaType.MANGA])
        assertEquals(0, result[MediaType.ANIME])
        assertEquals(0, result[MediaType.GAME])
    }

    @Test
    fun `observeTypeCounts emits zero for all entries when database is empty`() = runTest {
        val repo = createRepository(MutableStateFlow(emptyList()))

        val result = repo.observeTypeCounts().first()

        assertEquals(MediaType.entries.size, result.size)
        MediaType.entries.forEach { mediaType ->
            assertEquals(0, result[mediaType])
        }
    }
}
