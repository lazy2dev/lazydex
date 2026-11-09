package app.lazydex.data.coil

import app.lazydex.data.cache.CoverCache
import app.lazydex.domain.model.MediaItem
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fetcher based on Komikku's eu.kanade.tachiyomi.data.coil.MangaCoverFetcher.
 * Checks CoverCache on disk first; only downloads over HTTP if missing,
 * streaming directly into CoverCache so it is never fetched over network again.
 */
class CoverFetcher(
    private val url: String?,
    private val localPath: String?,
    private val mediaId: String?,
    private val options: Options,
    private val coverCache: CoverCache,
    private val callFactory: Call.Factory,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // 1. Custom / local path if specified and exists
        if (!localPath.isNullOrBlank()) {
            val localFile = File(localPath)
            if (localFile.exists()) {
                return fileLoader(localFile)
            }
        }

        // 2. Custom cover file by mediaId if exists
        if (!mediaId.isNullOrBlank()) {
            val customCoverFile = coverCache.getCustomCoverFile(mediaId)
            if (customCoverFile.exists()) {
                return fileLoader(customCoverFile)
            }
        }

        // 3. Remote URL
        val rawUrl = url?.trim()
        if (rawUrl.isNullOrBlank()) error("No cover specified")

        return when {
            rawUrl.startsWith("/") || rawUrl.startsWith("file://") -> {
                val path = if (rawUrl.startsWith("file://")) rawUrl.substringAfter("file://") else rawUrl
                fileLoader(File(path))
            }
            rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true) -> {
                httpLoader(rawUrl)
            }
            else -> error("Invalid cover source: $rawUrl")
        }
    }

    private suspend fun httpLoader(rawUrl: String): FetchResult {
        val cacheFile = coverCache.getCoverFile(rawUrl)
        if (cacheFile != null && cacheFile.exists() && options.diskCachePolicy.readEnabled) {
            return fileLoader(cacheFile)
        }

        val request = Request.Builder()
            .url(rawUrl)
            .build()

        val response = callFactory.newCall(request).await()
        if (!response.isSuccessful && response.code != 304) {
            response.close()
            throw IOException("Failed to download cover: HTTP ${response.code} ${response.message}")
        }

        val responseBody = checkNotNull(response.body) { "Empty response body" }
        try {
            if (cacheFile != null && options.diskCachePolicy.writeEnabled) {
                cacheFile.parentFile?.mkdirs()
                cacheFile.delete()
                cacheFile.sink().buffer().use { sink ->
                    sink.writeAll(responseBody.source())
                }
                if (cacheFile.exists()) {
                    return fileLoader(cacheFile)
                }
            }

            return SourceFetchResult(
                source = ImageSource(source = responseBody.source(), fileSystem = FileSystem.SYSTEM),
                mimeType = "image/*",
                dataSource = DataSource.NETWORK,
            )
        } catch (e: Exception) {
            responseBody.close()
            cacheFile?.delete()
            throw e
        }
    }

    private fun fileLoader(file: File): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
                diskCacheKey = file.absolutePath,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    class CoverModelFactory(
        private val coverCache: CoverCache,
        private val callFactory: Call.Factory,
    ) : Fetcher.Factory<CoverModel> {
        override fun create(data: CoverModel, options: Options, imageLoader: ImageLoader): Fetcher {
            return CoverFetcher(
                url = data.url,
                localPath = data.localPath,
                mediaId = data.mediaId,
                options = options,
                coverCache = coverCache,
                callFactory = callFactory,
                imageLoader = imageLoader,
            )
        }
    }

    class MediaItemFactory(
        private val coverCache: CoverCache,
        private val callFactory: Call.Factory,
    ) : Fetcher.Factory<MediaItem> {
        override fun create(data: MediaItem, options: Options, imageLoader: ImageLoader): Fetcher {
            return CoverFetcher(
                url = data.coverImageUrl,
                localPath = data.coverImagePath,
                mediaId = data.id,
                options = options,
                coverCache = coverCache,
                callFactory = callFactory,
                imageLoader = imageLoader,
            )
        }
    }
}

private suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (_: Throwable) {}
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
    }
}
