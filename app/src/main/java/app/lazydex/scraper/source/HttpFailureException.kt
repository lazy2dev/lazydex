package app.lazydex.scraper.source

import java.io.IOException

class HttpFailureException(val code: Int, message: String) : IOException(message)
