package app.lazydex.data.anilist

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class GraphQLError(val message: String? = null)

// Viewer DTOs
@Serializable
data class ViewerResponse(
    val data: ViewerData? = null,
    val errors: List<GraphQLError>? = null
)
@Serializable
data class ViewerData(val Viewer: ALUser? = null)
@Serializable
data class ALUser(
    val id: Long,
    val name: String,
    val mediaListOptions: ALMediaListOptions? = null
)
@Serializable
data class ALMediaListOptions(val scoreFormat: String? = null)

// MALSync-Style Page MediaList DTOs
@Serializable
data class PageMediaListResponse(
    val data: PageMediaListData? = null,
    val errors: List<GraphQLError>? = null
)
@Serializable
data class PageMediaListData(val Page: ALPage? = null)
@Serializable
data class ALPage(
    val pageInfo: ALPageInfo? = null,
    val mediaList: List<ALMediaListEntry>? = null
)
@Serializable
data class ALPageInfo(
    val hasNextPage: Boolean? = false
)

// Legacy MediaListCollection DTOs (retained for backward compatibility)
@Serializable
data class MediaListCollectionResponse(
    val data: MediaListCollectionData? = null,
    val errors: List<GraphQLError>? = null
)
@Serializable
data class MediaListCollectionData(val MediaListCollection: ALMediaListCollection? = null)
@Serializable
data class ALMediaListCollection(
    val lists: List<ALMediaListGroup>? = null,
    val hasNextChunk: Boolean? = false
)
@Serializable
data class ALMediaListGroup(
    val name: String? = null,
    val status: String? = null,
    val entries: List<ALMediaListEntry>? = null
)
@Serializable
data class ALMediaListEntry(
    val id: Long,
    val mediaId: Long,
    val status: String? = null,
    val scoreRaw: Int? = null,
    val progress: Int? = null,
    val progressVolumes: Int? = null,
    val `private`: Boolean? = false,
    val updatedAt: Long? = 0L,
    val media: ALMedia? = null
)
@Serializable
data class ALMedia(
    val id: Long,
    val siteUrl: String? = null,
    val title: ALTitle? = null,
    val coverImage: ALCoverImage? = null,
    val format: String? = null,
    val status: String? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val episodes: Int? = null,
    val description: String? = null,
    val averageScore: Int? = null,
    val genres: List<String>? = null,
    val countryOfOrigin: String? = null,
    val duration: Int? = null,
    val source: String? = null,
    val season: String? = null,
    val isAdult: Boolean? = false
)
@Serializable
data class ALTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
    val userPreferred: String? = null
)
@Serializable
data class ALCoverImage(
    val large: String? = null,
    val medium: String? = null
)

// SaveMediaListEntry DTOs
@Serializable
data class SaveMediaListEntryResponse(
    val data: SaveMediaListEntryData? = null,
    val errors: List<GraphQLError>? = null
)
@Serializable
data class SaveMediaListEntryData(val SaveMediaListEntry: ALMediaListEntry? = null)

// SearchMedia DTOs
@Serializable
data class SearchMediaResponse(
    val data: SearchMediaData? = null,
    val errors: List<GraphQLError>? = null
)
@Serializable
data class SearchMediaData(val Page: SearchMediaPage? = null)
@Serializable
data class SearchMediaPage(val media: List<ALMedia>? = null)

open class AnilistApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    open suspend fun getViewer(): ALUser {
        val query = """
            query {
              Viewer {
                id
                name
                mediaListOptions {
                  scoreFormat
                }
              }
            }
        """.trimIndent()

        val requestBody = json.encodeToString(GraphQLRequest.serializer(), GraphQLRequest(query))
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful && response.code != 401 && response.code != 429) {
            throw IOException("AniList HTTP ${response.code}: ${response.message}")
        }
        val bodyString = response.body?.string() ?: throw IOException("Empty response from AniList")
        val parsed = json.decodeFromString(ViewerResponse.serializer(), bodyString)
        if (!parsed.errors.isNullOrEmpty()) {
            val errorMsg = parsed.errors.firstOrNull()?.message ?: "Unknown GraphQL error"
            throw IOException("AniList Error: $errorMsg")
        }
        return parsed.data?.Viewer ?: throw IOException("Failed to parse Viewer data from AniList")
    }

    /**
     * MALSync-style page fetcher querying Page(page: $page, perPage: 100) { pageInfo { hasNextPage } mediaList { ... } }
     */
    open suspend fun fetchMediaListPage(
        userName: String,
        type: String, // "ANIME" or "MANGA"
        page: Int
    ): ALPage {
        val query = """
            query (${'$'}page: Int, ${'$'}userName: String, ${'$'}type: MediaType) {
              Page (page: ${'$'}page, perPage: 100) {
                pageInfo {
                  hasNextPage
                }
                mediaList (userName: ${'$'}userName, type: ${'$'}type) {
                  id
                  mediaId
                  status
                  scoreRaw: score(format: POINT_100)
                  progress
                  progressVolumes
                  private
                  updatedAt
                  media {
                    id
                    siteUrl
                    title { romaji english native userPreferred }
                    coverImage { large medium }
                    format
                    status
                    chapters
                    volumes
                    episodes
                    description
                    averageScore
                    genres
                    countryOfOrigin
                    duration
                    source
                    season
                    isAdult
                  }
                }
              }
            }
        """.trimIndent()

        val variables = mapOf(
            "page" to JsonPrimitive(page),
            "userName" to JsonPrimitive(userName),
            "type" to JsonPrimitive(type)
        )

        val requestBody = json.encodeToString(GraphQLRequest.serializer(), GraphQLRequest(query, variables))
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful && response.code != 401 && response.code != 429) {
            throw IOException("AniList HTTP ${response.code}: ${response.message}")
        }
        val bodyString = response.body?.string() ?: throw IOException("Empty response from AniList")
        val parsed = json.decodeFromString(PageMediaListResponse.serializer(), bodyString)
        if (!parsed.errors.isNullOrEmpty()) {
            val errorMsg = parsed.errors.firstOrNull()?.message ?: "Unknown GraphQL error"
            throw IOException("AniList Error: $errorMsg")
        }
        return parsed.data?.Page ?: ALPage(ALPageInfo(false), emptyList())
    }

    open suspend fun fetchMediaListChunk(
        userId: Long,
        type: String, // "ANIME" or "MANGA"
        chunk: Int,
        perPage: Int = 100
    ): ALMediaListCollection {
        val query = """
            query (${'$'}userId: Int, ${'$'}type: MediaType, ${'$'}chunk: Int, ${'$'}perChunk: Int) {
              MediaListCollection(userId: ${'$'}userId, type: ${'$'}type, chunk: ${'$'}chunk, perChunk: ${'$'}perChunk) {
                lists {
                  name
                  status
                  entries {
                    id
                    mediaId
                    status
                    scoreRaw: score(format: POINT_100)
                    progress
                    progressVolumes
                    private
                    updatedAt
                    media {
                      id
                      siteUrl
                      title { romaji english native userPreferred }
                      coverImage { large medium }
                      format
                      status
                      chapters
                      volumes
                      episodes
                      description
                      averageScore
                      genres
                      countryOfOrigin
                      duration
                      source
                      season
                      isAdult
                    }
                  }
                }
                hasNextChunk
              }
            }
        """.trimIndent()

        val variables = mapOf(
            "userId" to JsonPrimitive(userId),
            "type" to JsonPrimitive(type),
            "chunk" to JsonPrimitive(chunk),
            "perChunk" to JsonPrimitive(perPage)
        )

        val requestBody = json.encodeToString(GraphQLRequest.serializer(), GraphQLRequest(query, variables))
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful && response.code != 401 && response.code != 429) {
            throw IOException("AniList HTTP ${response.code}: ${response.message}")
        }
        val bodyString = response.body?.string() ?: throw IOException("Empty response from AniList")
        val parsed = json.decodeFromString(MediaListCollectionResponse.serializer(), bodyString)
        if (!parsed.errors.isNullOrEmpty()) {
            val errorMsg = parsed.errors.firstOrNull()?.message ?: "Unknown GraphQL error"
            throw IOException("AniList Error: $errorMsg")
        }
        return parsed.data?.MediaListCollection ?: ALMediaListCollection(emptyList(), false)
    }

    open suspend fun saveMediaListEntry(
        id: Long? = null,
        mediaId: Long,
        status: String,
        progress: Int,
        progressVolumes: Int? = null,
        scoreRaw: Int? = null,
        isPrivate: Boolean = false
    ): ALMediaListEntry {
        val query = """
            mutation (${'$'}id: Int, ${'$'}mediaId: Int, ${'$'}status: MediaListStatus, ${'$'}progress: Int, ${'$'}progressVolumes: Int, ${'$'}scoreRaw: Int, ${'$'}private: Boolean) {
              SaveMediaListEntry(
                id: ${'$'}id
                mediaId: ${'$'}mediaId
                status: ${'$'}status
                progress: ${'$'}progress
                progressVolumes: ${'$'}progressVolumes
                scoreRaw: ${'$'}scoreRaw
                private: ${'$'}private
              ) {
                id
                mediaId
                status
                scoreRaw: score(format: POINT_100)
                progress
                progressVolumes
                updatedAt
              }
            }
        """.trimIndent()

        val variables = mutableMapOf<String, JsonElement>(
            "mediaId" to JsonPrimitive(mediaId),
            "status" to JsonPrimitive(status),
            "progress" to JsonPrimitive(progress),
            "private" to JsonPrimitive(isPrivate)
        )
        if (id != null && id > 0) variables["id"] = JsonPrimitive(id)
        if (progressVolumes != null) variables["progressVolumes"] = JsonPrimitive(progressVolumes)
        if (scoreRaw != null && scoreRaw > 0) variables["scoreRaw"] = JsonPrimitive(scoreRaw)

        val requestBody = json.encodeToString(GraphQLRequest.serializer(), GraphQLRequest(query, variables))
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful && response.code != 401 && response.code != 429) {
            throw IOException("AniList HTTP ${response.code}: ${response.message}")
        }
        val bodyString = response.body?.string() ?: throw IOException("Empty response from AniList")
        val parsed = json.decodeFromString(SaveMediaListEntryResponse.serializer(), bodyString)
        if (!parsed.errors.isNullOrEmpty()) {
            val errorMsg = parsed.errors.firstOrNull()?.message ?: "Unknown GraphQL error"
            throw IOException("AniList Error: $errorMsg")
        }
        return parsed.data?.SaveMediaListEntry ?: throw IOException("Failed to save entry on AniList")
    }

    open suspend fun searchMedia(queryText: String, type: String? = null): List<ALMedia> {
        val query = """
            query (${'$'}search: String, ${'$'}type: MediaType) {
              Page(perPage: 20) {
                media(search: ${'$'}search, type: ${'$'}type) {
                  id
                  siteUrl
                  title { romaji english native userPreferred }
                  coverImage { large medium }
                  format
                  status
                  chapters
                  volumes
                  episodes
                  description
                  averageScore
                  genres
                  countryOfOrigin
                  duration
                  source
                  season
                  isAdult
                }
              }
            }
        """.trimIndent()

        val variables = mutableMapOf<String, JsonElement>(
            "search" to JsonPrimitive(queryText)
        )
        if (type != null) variables["type"] = JsonPrimitive(type)

        val requestBody = json.encodeToString(GraphQLRequest.serializer(), GraphQLRequest(query, variables))
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful && response.code != 401 && response.code != 429) {
            throw IOException("AniList HTTP ${response.code}: ${response.message}")
        }
        val bodyString = response.body?.string() ?: throw IOException("Empty response from AniList")
        val parsed = json.decodeFromString(SearchMediaResponse.serializer(), bodyString)
        if (!parsed.errors.isNullOrEmpty()) {
            val errorMsg = parsed.errors.firstOrNull()?.message ?: "Unknown GraphQL error"
            throw IOException("AniList Error: $errorMsg")
        }
        return parsed.data?.Page?.media ?: emptyList()
    }
}

