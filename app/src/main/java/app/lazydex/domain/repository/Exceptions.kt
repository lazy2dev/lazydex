package app.lazydex.domain.repository

sealed class LazyDexException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class DuplicateUrlException(message: String, cause: Throwable? = null) : LazyDexException(message, cause)

class ImportFailedException(message: String, cause: Throwable? = null) : LazyDexException(message, cause)
