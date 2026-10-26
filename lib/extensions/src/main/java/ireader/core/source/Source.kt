package ireader.core.source

import ireader.core.source.model.BookInfo
import ireader.core.source.model.BooksPageInfo
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.CommandList
import okhttp3.Headers
import okhttp3.OkHttpClient

interface Source {
    val id: Long
    val name: String
    val lang: String

    suspend fun getPopular(page: Int): BooksPageInfo
    suspend fun getSearch(page: Int, query: String, filters: CommandList): BooksPageInfo
    suspend fun getBookDetails(book: BookInfo): BookInfo = book
    suspend fun getChapterList(book: BookInfo): List<ChapterInfo> = emptyList()
}

abstract class HttpSource : Source {
    abstract val baseUrl: String
    open val client: OkHttpClient = OkHttpClient()
    open val headers: Headers = Headers.Builder().build()
}
