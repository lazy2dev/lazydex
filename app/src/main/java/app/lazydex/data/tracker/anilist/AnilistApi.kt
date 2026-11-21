package app.lazydex.data.tracker.anilist

import app.lazydex.data.tracker.anilist.dto.AniListApiException
import app.lazydex.data.tracker.anilist.dto.AniListFuzzyDateInput
import app.lazydex.data.tracker.anilist.dto.AniListGraphQLError
import app.lazydex.data.tracker.anilist.dto.AniListGraphQLResponse
import app.lazydex.data.tracker.anilist.dto.AniListMedia
import app.lazydex.data.tracker.anilist.dto.AniListMediaListEntry
import app.lazydex.data.tracker.anilist.dto.AniListSavedEntry
import app.lazydex.data.tracker.anilist.dto.AniListViewer
import app.lazydex.data.tracker.anilist.dto.CurrentUserData
import app.lazydex.data.tracker.anilist.dto.MediaListCollection
import app.lazydex.data.tracker.anilist.dto.MediaListCollectionData
import app.lazydex.data.tracker.anilist.dto.SaveMediaListEntryData
import app.lazydex.data.tracker.anilist.dto.SearchMediaPageData
import app.lazydex.data.tracker.anilist.dto.SingleMediaData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class AnilistApi(
    private val okHttpClient: OkHttpClient,
    private val tokenStore: AnilistTokenStore,
    private val limiter: TokenBucketLimiter,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val json: Json = defaultJson,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://graphql.anilist.co"
        const val DEFAULT_PER_CHUNK = 150
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        const val MEDIA_LIST_COLLECTION_QUERY = """
query (${'$'}userId: Int, ${'$'}type: MediaType, ${'$'}chunk: Int, ${'$'}perChunk: Int) {
  MediaListCollection(userId: ${'$'}userId, type: ${'$'}type, chunk: ${'$'}chunk, perChunk: ${'$'}perChunk) {
    hasNextChunk
    lists {
      name
      isCustomList
      status
      entries {
        id
        mediaId
        status
        scoreRaw: score(format: POINT_100)
        progress
        progressVolumes
        repeat
        private
        notes
        startedAt { year month day }
        completedAt { year month day }
        updatedAt
        createdAt
        media {
          id
          idMal
          format
          countryOfOrigin
          status
          isAdult
          episodes
          duration
          chapters
          volumes
          genres
          startDate { year month day }
          coverImage { large extraLarge }
          bannerImage
          averageScore
          title { userPreferred english romaji native }
        }
      }
    }
  }
}
"""

        const val MEDIA_BATCH_METADATA_QUERY = """
query (${'$'}ids: [Int], ${'$'}type: MediaType) {
  Page(page: 1, perPage: 50) {
    media(id_in: ${'$'}ids, type: ${'$'}type) {
      id
      description(asHtml: false)
      genres
      tags {
        name
        isMediaSpoiler
      }
    }
  }
}
"""

        const val MEDIA_DETAILS_QUERY = """
query (${'$'}id: Int, ${'$'}type: MediaType) {
  Media(id: ${'$'}id, type: ${'$'}type) {
    id
    description(asHtml: false)
    genres
    tags {
      name
      isMediaSpoiler
    }
  }
}
"""

        const val SAVE_MEDIA_LIST_ENTRY_MUTATION = """
mutation (
  ${'$'}id: Int,
  ${'$'}mediaId: Int,
  ${'$'}status: MediaListStatus,
  ${'$'}progress: Int,
  ${'$'}progressVolumes: Int,
  ${'$'}scoreRaw: Int,
  ${'$'}repeat: Int,
  ${'$'}notes: String,
  ${'$'}private: Boolean,
  ${'$'}startedAt: FuzzyDateInput,
  ${'$'}completedAt: FuzzyDateInput
) {
  SaveMediaListEntry(
    id: ${'$'}id,
    mediaId: ${'$'}mediaId,
    status: ${'$'}status,
    progress: ${'$'}progress,
    progressVolumes: ${'$'}progressVolumes,
    scoreRaw: ${'$'}scoreRaw,
    repeat: ${'$'}repeat,
    notes: ${'$'}notes,
    private: ${'$'}private,
    startedAt: ${'$'}startedAt,
    completedAt: ${'$'}completedAt
  ) {
    id
    mediaId
    status
    score
    progress
    progressVolumes
    repeat
    notes
    updatedAt
  }
}
"""

        const val CURRENT_USER_QUERY = """
query CurrentUser {
  Viewer {
    id
    name
    mediaListOptions {
      scoreFormat
    }
    options {
      displayAdultContent
    }
  }
}
"""

        const val SEARCH_MEDIA_QUERY = """
query (${'$'}search: String, ${'$'}type: MediaType, ${'$'}perPage: Int) {
  Page(perPage: ${'$'}perPage) {
    media(search: ${'$'}search, type: ${'$'}type) {
      id
      title { userPreferred english romaji native }
      coverImage { large extraLarge medium }
      format
      countryOfOrigin
      status
      episodes
      chapters
      volumes
      averageScore
      startDate { year month day }
    }
  }
}
"""
    }

    /**
     * Executes paginated MediaListCollection query for [userId] and [type] ("MANGA" or "ANIME").
     * Requests are throttled using [TokenBucketLimiter.Priority.BACKGROUND] by default.
     */
    suspend fun getMediaListCollection(
        userId: Int,
        type: String,
        chunk: Int = 1,
        perChunk: Int = DEFAULT_PER_CHUNK,
        priority: TokenBucketLimiter.Priority = TokenBucketLimiter.Priority.BACKGROUND
    ): MediaListCollection {
        val variables = buildJsonObject {
            put("userId", userId)
            put("type", type)
            put("chunk", chunk)
            put("perChunk", perChunk)
        }

        val responseData = execute(
            query = MEDIA_LIST_COLLECTION_QUERY,
            variables = variables,
            deserializer = MediaListCollectionData.serializer(),
            priority = priority
        )

        return responseData.mediaListCollection ?: MediaListCollection()
    }

    /**
     * Convenience method returning deduplicated entries and [hasNextChunk] flag.
     * Custom list duplicates are removed via `distinctBy { it.media.id }`.
     * Applies adaptive chunk shrinking: on EOF or network failure, halves perChunk (min 25) with up to 3 retries.
     */
    suspend fun getDeduplicatedEntries(
        userId: Int,
        type: String,
        chunk: Int = 1,
        perChunk: Int = DEFAULT_PER_CHUNK,
        priority: TokenBucketLimiter.Priority = TokenBucketLimiter.Priority.BACKGROUND
    ): Pair<List<AniListMediaListEntry>, Boolean> {
        var currentPerChunk = perChunk
        var attempts = 0
        while (true) {
            try {
                val collection = getMediaListCollection(userId, type, chunk, currentPerChunk, priority)
                return Pair(collection.deduplicatedEntries, collection.hasNextChunk)
            } catch (e: Exception) {
                attempts++
                val isRecoverable = (e is IOException || e is AniListApiException) && attempts < 3 && currentPerChunk > 25
                if (isRecoverable) {
                    currentPerChunk = (currentPerChunk / 2).coerceAtLeast(25)
                    delay(500L * attempts)
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * Batch queries metadata (synopsis, tags, genres) for up to 50 media IDs in a single request.
     */
    suspend fun getMediaBatchMetadata(
        ids: List<Int>,
        type: String,
        priority: TokenBucketLimiter.Priority = TokenBucketLimiter.Priority.BACKGROUND
    ): List<AniListMedia> {
        if (ids.isEmpty()) return emptyList()
        val aniListType = if (type.equals("ANIME", ignoreCase = true)) "ANIME" else "MANGA"
        val variables = buildJsonObject {
            put("ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
            put("type", aniListType)
        }
        val response = execute(
            query = MEDIA_BATCH_METADATA_QUERY,
            variables = variables,
            deserializer = SearchMediaPageData.serializer(),
            priority = priority
        )
        return response.page?.media ?: emptyList()
    }

    /**
     * Queries detailed metadata for a single media ID at interactive priority.
     */
    suspend fun getMediaDetails(
        id: Int,
        type: String,
        priority: TokenBucketLimiter.Priority = TokenBucketLimiter.Priority.INTERACTIVE
    ): AniListMedia? {
        val aniListType = if (type.equals("ANIME", ignoreCase = true)) "ANIME" else "MANGA"
        val variables = buildJsonObject {
            put("id", id)
            put("type", aniListType)
        }
        val response = execute(
            query = MEDIA_DETAILS_QUERY,
            variables = variables,
            deserializer = SingleMediaData.serializer(),
            priority = priority
        )
        return response.media
    }

    data class SaveMediaListEntryParams(
        val id: Int? = null,
        val mediaId: Int? = null,
        val status: String? = null,
        val progress: Int? = null,
        val progressVolumes: Int? = null,
        val scoreRaw: Int? = null,
        val repeat: Int? = null,
        val notes: String? = null,
        val isPrivate: Boolean? = null,
        val startedAt: AniListFuzzyDateInput? = null,
        val completedAt: AniListFuzzyDateInput? = null
    )

    /**
     * Executes SaveMediaListEntry mutation.
     * Requests are dispatched with [TokenBucketLimiter.Priority.INTERACTIVE] priority by default.
     */
    suspend fun saveMediaListEntry(
        params: SaveMediaListEntryParams,
        priority: TokenBucketLimiter.Priority = TokenBucketLimiter.Priority.INTERACTIVE
    ): AniListSavedEntry {
        val variables = buildJsonObject {
            params.id?.let { put("id", it) }
            params.mediaId?.let { put("mediaId", it) }
            params.status?.let { put("status", it) }
            params.progress?.let { put("progress", it) }
            params.progressVolumes?.let { put("progressVolumes", it) }
            params.scoreRaw?.let { put("scoreRaw", it) }
            params.repeat?.let { put("repeat", it) }
            params.notes?.let { put("notes", it) }
            params.isPrivate?.let { put("private", it) }
            params.startedAt?.let { date ->
                putJsonObject("startedAt") {
                    date.year?.let { put("year", it) }
                    date.month?.let { put("month", it) }
                    date.day?.let { put("day", it) }
                }
            }
            params.completedAt?.let { date ->
                putJsonObject("completedAt") {
                    date.year?.let { put("year", it) }
                    date.month?.let { put("month", it) }
                    date.day?.let { put("day", it) }
                }
            }
        }

        val responseData = execute(
            query = SAVE_MEDIA_LIST_ENTRY_MUTATION,
            variables = variables,
            deserializer = SaveMediaListEntryData.serializer(),
            priority = priority
        )

        return responseData.saveMediaListEntry ?: throw AniListApiException(
            errors = listOf(AniListGraphQLError("SaveMediaListEntry returned no data")),
            httpCode = 200
        )
    }

    /**
     * Overloaded helper for [saveMediaListEntry] accepting individual arguments.
     */
    suspend fun saveMediaListEntry(
        id: Int? = null,
        mediaId: Int? = null,
        status: String? = null,
        progress: Int? = null,
        progressVolumes: Int? = null,
        scoreRaw: Int? = null,
        repeat: Int? = null,
        notes: String? = null,
        isPrivate: Boolean? = null,
        startedAt: AniListFuzzyDateInput? = null,
        completedAt: AniListFuzzyDateInput? = null,
        priority: TokenBucketLimiter.Priority = TokenBucketLimiter.Priority.INTERACTIVE
    ): AniListSavedEntry = saveMediaListEntry(
        SaveMediaListEntryParams(
            id = id,
            mediaId = mediaId,
            status = status,
            progress = progress,
            progressVolumes = progressVolumes,
            scoreRaw = scoreRaw,
            repeat = repeat,
            notes = notes,
            isPrivate = isPrivate,
            startedAt = startedAt,
            completedAt = completedAt
        ),
        priority = priority
    )

    /**
     * Queries the authenticated AniList Viewer (current user profile).
     */
    suspend fun getCurrentUser(
        priority: TokenBucketLimiter.Priority = TokenBucketLimiter.Priority.INTERACTIVE
    ): AniListViewer {
        val responseData = execute(
            query = CURRENT_USER_QUERY,
            variables = buildJsonObject {},
            deserializer = CurrentUserData.serializer(),
            priority = priority
        )

        return responseData.viewer ?: throw AniListApiException(
            errors = listOf(AniListGraphQLError("CurrentUser returned no viewer data")),
            httpCode = 200
        )
    }

    /**
     * Searches AniList for media matching [query] and [type] ("MANGA" or "ANIME").
     */
    suspend fun searchMedia(
        query: String,
        type: String,
        perPage: Int = 10,
        priority: TokenBucketLimiter.Priority = TokenBucketLimiter.Priority.INTERACTIVE
    ): List<AniListMedia> {
        if (query.isBlank()) return emptyList()
        val variables = buildJsonObject {
            put("search", query.trim())
            put("type", type.uppercase())
            put("perPage", perPage)
        }

        val responseData = execute(
            query = SEARCH_MEDIA_QUERY,
            variables = variables,
            deserializer = SearchMediaPageData.serializer(),
            priority = priority
        )

        return responseData.page?.media ?: emptyList()
    }

    private suspend fun <T> execute(
        query: String,
        variables: JsonObject,
        deserializer: KSerializer<T>,
        priority: TokenBucketLimiter.Priority,
        maxRetries: Int = 2
    ): T {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }

        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

        var attempts = 0
        while (true) {
            limiter.acquire(priority)

            val requestBuilder = Request.Builder()
                .url(baseUrl)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")

            tokenStore.accessToken?.takeIf { it.isNotBlank() }?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val request = requestBuilder.build()

            val response = withContext(ioDispatcher) {
                okHttpClient.newCall(request).execute()
            }

            val httpCode = response.code

            if (httpCode == 429) {
                val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull() ?: 60L
                response.close()
                limiter.notifyRateLimited(retryAfterSeconds)
                attempts++
                if (attempts > maxRetries) {
                    throw AniListApiException(
                        errors = listOf(AniListGraphQLError(message = "Rate limit exceeded (HTTP 429)", status = 429)),
                        httpCode = 429
                    )
                }
                continue
            }

            val responseBody = response.use { it.body?.string() } ?: ""

            if (!response.isSuccessful) {
                val parsedError = runCatching {
                    json.decodeFromString<AniListGraphQLResponse<JsonElement>>(responseBody)
                }.getOrNull()
                val errors = parsedError?.errors ?: listOf(
                    AniListGraphQLError(message = "HTTP $httpCode: ${response.message}", status = httpCode)
                )
                throw AniListApiException(errors = errors, httpCode = httpCode)
            }

            val graphQLResponse = try {
                json.decodeFromString(AniListGraphQLResponse.serializer(deserializer), responseBody)
            } catch (e: Exception) {
                throw IOException("Failed to parse AniList GraphQL response: ${e.message}", e)
            }

            if (!graphQLResponse.errors.isNullOrEmpty()) {
                throw AniListApiException(errors = graphQLResponse.errors, httpCode = httpCode)
            }

            return graphQLResponse.data ?: throw AniListApiException(
                errors = listOf(AniListGraphQLError("GraphQL response returned null data")),
                httpCode = httpCode
            )
        }
    }
}

