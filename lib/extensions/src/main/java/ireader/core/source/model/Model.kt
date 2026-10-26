package ireader.core.source.model

data class BookInfo(
    val title: String,
    val key: String,
    val cover: String = "",
    val author: String = "",
    val description: String = "",
    val genres: List<String> = emptyList(),
    val status: Int = 0
)

data class ChapterInfo(
    val name: String,
    val key: String,
    val dateUpload: Long = 0L,
    val number: Float = -1f
)

data class BooksPageInfo(
    val books: List<BookInfo>,
    val hasNextPage: Boolean = false
)

data class Listing(
    val name: String
)

sealed class Command<T>(val name: String, var value: T)

class CommandList(val list: List<Command<*>> = emptyList()) : List<Command<*>> by list {
    constructor(vararg commands: Command<*>) : this(commands.toList())
}
