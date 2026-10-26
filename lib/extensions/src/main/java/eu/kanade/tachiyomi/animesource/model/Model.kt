package eu.kanade.tachiyomi.animesource.model

import okhttp3.Headers

interface SAnime {
    var url: String
    var title: String
    var artist: String?
    var author: String?
    var description: String?
    var genre: String?
    var status: Int
    var thumbnail_url: String?
    var initialized: Boolean

    companion object {
        fun create(): SAnime = SAnimeImpl()
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6
    }
}

class SAnimeImpl : SAnime {
    override var url: String = ""
    override var title: String = ""
    override var artist: String? = null
    override var author: String? = null
    override var description: String? = null
    override var genre: String? = null
    override var status: Int = SAnime.UNKNOWN
    override var thumbnail_url: String? = null
    override var initialized: Boolean = false
}

interface SEpisode {
    var url: String
    var name: String
    var date_upload: Long
    var episode_number: Float
    var scanlator: String?

    companion object {
        fun create(): SEpisode = SEpisodeImpl()
    }
}

class SEpisodeImpl : SEpisode {
    override var url: String = ""
    override var name: String = ""
    override var date_upload: Long = 0L
    override var episode_number: Float = -1f
    override var scanlator: String? = null
}

data class AnimesPage(
    val animes: List<SAnime>,
    val hasNextPage: Boolean
)

data class Video(
    val url: String,
    val quality: String,
    val videoUrl: String? = null,
    val headers: Headers? = null
)
