package app.lazydex.data.tracker.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.IOException

@Serializable
data class AniListGraphQLResponse<T>(
    val data: T? = null,
    val errors: List<AniListGraphQLError>? = null
)

@Serializable
data class AniListGraphQLError(
    val message: String,
    val status: Int? = null,
    val locations: List<AniListErrorLocation>? = null
)

@Serializable
data class AniListErrorLocation(
    val line: Int? = null,
    val column: Int? = null
)

class AniListApiException(
    val errors: List<AniListGraphQLError> = emptyList(),
    val httpCode: Int = 200,
    override val message: String = errors.firstOrNull()?.message ?: "AniList GraphQL Error (HTTP $httpCode)"
) : IOException(message) {
    val isUnauthorized: Boolean
        get() = httpCode == 401 || errors.any { it.status == 401 || it.message.contains("Invalid token", ignoreCase = true) }

    val isRateLimited: Boolean
        get() = httpCode == 429 || errors.any { it.status == 429 || it.message.contains("rate limit", ignoreCase = true) }
}

@Serializable
data class MediaListCollectionData(
    @SerialName("MediaListCollection")
    val mediaListCollection: MediaListCollection? = null
)

@Serializable
data class MediaListCollection(
    val hasNextChunk: Boolean = false,
    val lists: List<MediaListGroup> = emptyList()
) {
    /**
     * Flattens entries from all lists, filtering null media and deduplicating
     * by `media.id`, giving precedence to standard status lists over custom lists.
     */
    val deduplicatedEntries: List<AniListMediaListEntry>
        get() = lists
            .sortedBy { it.isCustomList }
            .flatMap { it.entries }
            .filter { it.media != null }
            .distinctBy { it.media!!.id }
}

@Serializable
data class MediaListGroup(
    val name: String? = null,
    val isCustomList: Boolean = false,
    val status: String? = null,
    val entries: List<AniListMediaListEntry> = emptyList()
)

@Serializable
data class AniListMediaListEntry(
    val id: Int,
    val mediaId: Int? = null,
    val status: String? = null,
    val scoreRaw: Int? = null,
    val progress: Int? = null,
    val progressVolumes: Int? = null,
    val repeat: Int? = null,
    @SerialName("private")
    val isPrivate: Boolean? = null,
    val notes: String? = null,
    val startedAt: AniListFuzzyDate? = null,
    val completedAt: AniListFuzzyDate? = null,
    val updatedAt: Long? = null,
    val media: AniListMedia? = null
) {
    val resolvedMediaId: Int?
        get() = mediaId ?: media?.id
}

@Serializable
data class AniListFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
)

@Serializable
data class AniListMedia(
    val id: Int,
    val idMal: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val isAdult: Boolean? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val genres: List<String> = emptyList(),
    val tags: List<AniListTag> = emptyList(),
    val coverImage: AniListCoverImage? = null,
    val bannerImage: String? = null,
    val title: AniListTitle? = null,
    val description: String? = null,
    val startDate: AniListFuzzyDate? = null
)

@Serializable
data class AniListTag(
    val name: String,
    val isMediaSpoiler: Boolean = false
)

@Serializable
data class SearchMediaPageData(
    @SerialName("Page")
    val page: SearchMediaPage? = null
)

@Serializable
data class SearchMediaPage(
    val media: List<AniListMedia> = emptyList()
)

@Serializable
data class AniListCoverImage(
    val large: String? = null,
    val extraLarge: String? = null,
    val medium: String? = null
) {
    val best: String?
        get() = extraLarge ?: large ?: medium
}

@Serializable
data class AniListTitle(
    val userPreferred: String? = null,
    val english: String? = null,
    val romaji: String? = null,
    @SerialName("native")
    val nativeTitle: String? = null
) {
    val best: String
        get() = userPreferred ?: english ?: romaji ?: nativeTitle ?: ""
}

@Serializable
data class SaveMediaListEntryData(
    @SerialName("SaveMediaListEntry")
    val saveMediaListEntry: AniListSavedEntry? = null
)

@Serializable
data class AniListSavedEntry(
    val id: Int,
    val mediaId: Int? = null,
    val status: String? = null,
    val score: Double? = null,
    val progress: Int? = null,
    val progressVolumes: Int? = null,
    val updatedAt: Long? = null
)

@Serializable
data class AniListFuzzyDateInput(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
)

@Serializable
data class CurrentUserData(
    @SerialName("Viewer")
    val viewer: AniListViewer? = null
)

@Serializable
data class AniListViewer(
    val id: Int,
    val name: String,
    val mediaListOptions: AniListMediaListOptions? = null,
    val options: AniListUserOptions? = null
)

@Serializable
data class AniListMediaListOptions(
    val scoreFormat: String? = null
)

@Serializable
data class AniListUserOptions(
    val displayAdultContent: Boolean = false
)

