package app.lazydex.data.sync

import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.UserStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

data class HarvestResult(
    val addedCount: Int,
    val updatedCount: Int
)

class InboundHarvester(
    private val dao: MediaItemDao,
    private val client: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun harvestFromAniList(userName: String): HarvestResult = withContext(Dispatchers.IO) {
        if (userName.isBlank()) return@withContext HarvestResult(0, 0)

        var totalAdded = 0
        var totalUpdated = 0

        val animeResult = harvestType(userName, "ANIME", MediaCategory.ANIME)
        totalAdded += animeResult.addedCount
        totalUpdated += animeResult.updatedCount

        val mangaResult = harvestType(userName, "MANGA", MediaCategory.MANGA)
        totalAdded += mangaResult.addedCount
        totalUpdated += mangaResult.updatedCount

        HarvestResult(totalAdded, totalUpdated)
    }

    private suspend fun harvestType(
        userName: String,
        mediaType: String,
        category: MediaCategory
    ): HarvestResult {
        val query = """
            query (${'$'}userName: String, ${'$'}type: MediaType) {
              MediaListCollection(userName: ${'$'}userName, type: ${'$'}type) {
                lists {
                  entries {
                    id
                    status
                    score(format: POINT_100)
                    progress
                    updatedAt
                    media {
                      id
                      title { english romaji native }
                      episodes
                      chapters
                      coverImage { extraLarge large }
                      description(asHtml: false)
                      genres
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                put("userName", userName)
                put("type", mediaType)
            }
        }.toString()

        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        var added = 0
        var updated = 0

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return HarvestResult(0, 0)
                val body = response.body?.string() ?: return HarvestResult(0, 0)
                val root = json.parseToJsonElement(body).jsonObject
                val lists = root["data"]?.jsonObject
                    ?.get("MediaListCollection")?.jsonObject
                    ?.get("lists")?.jsonArray ?: return HarvestResult(0, 0)

                for (listElem in lists) {
                    val entries = listElem.jsonObject["entries"]?.jsonArray ?: continue
                    for (entryElem in entries) {
                        val entry = entryElem.jsonObject
                        val media = entry["media"]?.jsonObject ?: continue
                        val remoteMediaId = media["id"]?.jsonPrimitive?.content ?: continue
                        val progress = entry["progress"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val remoteStatusStr = entry["status"]?.jsonPrimitive?.content ?: ""
                        val scoreRaw = entry["score"]?.jsonPrimitive?.content?.toDoubleOrNull()
                        val updatedAt = (entry["updatedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) * 1000L

                        val existingMatches = dao.findByExtraPattern("\"anilist_id\":\"$remoteMediaId\"")
                        val existing = existingMatches.firstOrNull()

                        if (existing == null) {
                            // Ingest as new local MediaItem
                            val titleObj = media["title"]?.jsonObject
                            val title = titleObj?.get("english")?.jsonPrimitive?.content
                                ?: titleObj?.get("romaji")?.jsonPrimitive?.content
                                ?: titleObj?.get("native")?.jsonPrimitive?.content
                                ?: "Untitled"

                            val altTitles = mutableListOf<String>()
                            titleObj?.get("romaji")?.jsonPrimitive?.content?.let { if (it != title) altTitles.add(it) }
                            titleObj?.get("native")?.jsonPrimitive?.content?.let { if (it != title) altTitles.add(it) }

                            val coverUrl = media["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.content
                                ?: media["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content

                            val total = if (category == MediaCategory.ANIME) {
                                media["episodes"]?.jsonPrimitive?.content?.toIntOrNull()
                            } else {
                                media["chapters"]?.jsonPrimitive?.content?.toIntOrNull()
                            }

                            val genres = media["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                            val description = media["description"]?.jsonPrimitive?.content ?: ""

                            val mappedStatus = mapAniListStatus(remoteStatusStr, category)
                            val ratingStars = scoreRaw?.takeIf { it > 0 }?.let { (it / 20.0).coerceIn(1.0, 5.0) }

                            val newItem = MediaItem(
                                id = UUID.randomUUID().toString(),
                                category = category,
                                title = title,
                                alternativeTitles = altTitles,
                                coverImageUrl = coverUrl,
                                currentProgress = progress,
                                totalItems = total,
                                userStatus = mappedStatus,
                                rating = ratingStars,
                                genres = genres,
                                description = description,
                                lastUpdated = if (updatedAt > 0) updatedAt else System.currentTimeMillis(),
                                dateAdded = System.currentTimeMillis(),
                                extraData = "{\"anilist_id\":\"$remoteMediaId\"}"
                            ).normalize()

                            dao.upsert(newItem.toEntity())
                            added++
                        } else {
                            // Forward-only progress update on existing local item
                            val localDomain = existing.toDomain() ?: continue
                            val forwardProgress = maxOf(localDomain.currentProgress, progress)
                            val isCompletedRemote = remoteStatusStr.equals("COMPLETED", ignoreCase = true)
                            val updatedStatus = if (isCompletedRemote) UserStatus.COMPLETED else localDomain.userStatus

                            val needsUpdate = forwardProgress != localDomain.currentProgress ||
                                    (isCompletedRemote && localDomain.userStatus != UserStatus.COMPLETED)

                            if (needsUpdate) {
                                val updatedItem = localDomain.copy(
                                    currentProgress = forwardProgress,
                                    userStatus = updatedStatus,
                                    lastUpdated = maxOf(localDomain.lastUpdated, updatedAt)
                                ).normalize()
                                dao.upsert(updatedItem.toEntity())
                                updated++
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Network failure or parsing issue - local state is unaffected
        }

        return HarvestResult(added, updated)
    }

    private fun mapAniListStatus(status: String, category: MediaCategory): UserStatus {
        return when (status.uppercase()) {
            "CURRENT" -> when (category) {
                MediaCategory.ANIME -> UserStatus.WATCHING
                else -> UserStatus.READING
            }
            "COMPLETED" -> UserStatus.COMPLETED
            "PAUSED" -> UserStatus.ON_HOLD
            "DROPPED" -> UserStatus.DROPPED
            "PLANNING" -> UserStatus.PLAN_TO
            else -> UserStatus.PLAN_TO
        }
    }

    private fun MediaItem.toEntity(): app.lazydex.data.local.entity.MediaItemEntity {
        val converters = app.lazydex.data.local.converter.Converters()
        return app.lazydex.data.local.entity.MediaItemEntity(
            id = id,
            category = category.name,
            title = title,
            alternativeTitles = converters.fromList(alternativeTitles),
            sourceUrl = sourceUrl,
            coverImagePath = coverImagePath,
            coverImageUrl = coverImageUrl,
            currentProgress = currentProgress,
            totalItems = totalItems,
            userStatus = userStatus.name,
            rating = rating,
            notes = notes,
            genres = converters.fromList(genres),
            tags = converters.fromList(tags),
            author = author,
            description = description,
            startDate = startDate,
            endDate = endDate,
            lastUpdated = lastUpdated,
            dateAdded = dateAdded,
            extraData = extraData,
            isDeleted = isDeleted
        )
    }

    private fun app.lazydex.data.local.entity.MediaItemEntity.toDomain(): MediaItem? {
        val converters = app.lazydex.data.local.converter.Converters()
        return try {
            val cat = MediaCategory.fromString(category) ?: return null
            val stat = UserStatus.fromString(userStatus) ?: return null
            MediaItem(
                id = id,
                category = cat,
                title = title,
                alternativeTitles = converters.toList(alternativeTitles),
                sourceUrl = sourceUrl,
                coverImagePath = coverImagePath,
                coverImageUrl = coverImageUrl,
                currentProgress = currentProgress,
                totalItems = totalItems,
                userStatus = stat,
                rating = rating,
                notes = notes,
                genres = converters.toList(genres),
                tags = converters.toList(tags),
                author = author,
                description = description,
                startDate = startDate,
                endDate = endDate,
                lastUpdated = lastUpdated,
                dateAdded = dateAdded,
                extraData = extraData,
                isDeleted = isDeleted
            )
        } catch (e: Exception) {
            null
        }
    }
}
