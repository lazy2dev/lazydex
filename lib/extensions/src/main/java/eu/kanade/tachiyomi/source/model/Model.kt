package eu.kanade.tachiyomi.source.model

import android.net.Uri

interface SManga {
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
        fun create(): SManga = SMangaImpl()
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6
    }
}

class SMangaImpl : SManga {
    override var url: String = ""
    override var title: String = ""
    override var artist: String? = null
    override var author: String? = null
    override var description: String? = null
    override var genre: String? = null
    override var status: Int = SManga.UNKNOWN
    override var thumbnail_url: String? = null
    override var initialized: Boolean = false
}

interface SChapter {
    var url: String
    var name: String
    var date_upload: Long
    var chapter_number: Float
    var scanlator: String?

    companion object {
        fun create(): SChapter = SChapterImpl()
    }
}

class SChapterImpl : SChapter {
    override var url: String = ""
    override var name: String = ""
    override var date_upload: Long = 0L
    override var chapter_number: Float = -1f
    override var scanlator: String? = null
}

data class MangasPage(
    val mangas: List<SManga>,
    val hasNextPage: Boolean
)

open class Page(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
    var uri: Uri? = null
)

sealed class Filter<T>(val name: String, var state: T) {
    open class Header(name: String) : Filter<Any>(name, "")
    open class Separator(name: String = "") : Filter<Any>(name, "")
    open class Select<V>(name: String, val values: Array<V>, state: Int = 0) : Filter<Int>(name, state)
    open class Text(name: String, state: String = "") : Filter<String>(name, state)
    open class CheckBox(name: String, state: Boolean = false) : Filter<Boolean>(name, state)
    open class TriState(name: String, state: Int = 0) : Filter<Int>(name, state)
    open class Sort(name: String, val values: Array<String>, state: Selection? = null) : Filter<Sort.Selection?>(name, state) {
        data class Selection(val index: Int, val ascending: Boolean)
    }
    open class Group<V>(name: String, state: List<V>) : Filter<List<V>>(name, state)
}

class FilterList(val list: List<Filter<*>> = emptyList()) : List<Filter<*>> by list {
    constructor(vararg filters: Filter<*>) : this(filters.toList())
}
