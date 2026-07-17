package app.lazydex.scraper

import app.lazydex.util.UrlNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.InetAddress

class SafeDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        for (address in addresses) {
            if (address.isLoopbackAddress || 
                address.isSiteLocalAddress || 
                address.isLinkLocalAddress || 
                address.isAnyLocalAddress) {
                throw IOException("Access to local/private network addresses is blocked")
            }
        }
        return addresses
    }
}

class MetadataScraper(private val okHttpClient: OkHttpClient) {

    suspend fun scrape(url: String): Result<ScrapedMetadata> = withTimeout(30_000L) {
        withContext(Dispatchers.IO) {
            try {
                val normalizedUrl = UrlNormalizer.normalize(url)
                if (!validateUrl(normalizedUrl)) {
                    return@withContext Result.failure(IllegalArgumentException("Invalid URL or unsafe format. Only public HTTPS URLs are supported."))
                }

                val request = Request.Builder().url(normalizedUrl).build()
                val call = okHttpClient.newCall(request)
                
                currentCoroutineContext()[Job]?.invokeOnCompletion {
                    call.cancel()
                }

                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected HTTP response code $response")
                    val body = response.body ?: throw IOException("Empty response body")
                    
                    val subtype = body.contentType()?.subtype ?: "html"
                    if (!subtype.contains("html", ignoreCase = true) && !subtype.contains("xml", ignoreCase = true)) {
                        throw IOException("URL does not point to an HTML/XML page (got $subtype)")
                    }

                    val contentLength = body.contentLength()
                    if (contentLength > MAX_SCRAPE_BYTES) throw IOException("File size limit exceeded")
                    
                    val limitedSource = SizeLimitedSource(body.source(), MAX_SCRAPE_BYTES).buffer()
                    
                    val doc = withContext(Dispatchers.Default) {
                        // Pass null for charset to allow Jsoup to sniff the true encoding from headers/<meta> tags (prevents mojibake)
                        Jsoup.parse(limitedSource.inputStream(), null, normalizedUrl)
                    }
                    
                    val title = extractTitle(doc)
                    val imageUrl = extractImageUrl(doc)
                    val alternativeTitles = extractAlternativeTitles(doc)
                    
                    Result.success(ScrapedMetadata(title, imageUrl, alternativeTitles))
                }
            } catch (e: TimeoutCancellationException) {
                Result.failure(Exception("Request timed out. The site may be slow or unreachable.", e))
            } catch (e: Exception) {
                if (e is IOException && e.message == "Canceled" || !currentCoroutineContext().isActive) {
                    throw CancellationException("Scrape cancelled", e)
                }
                Result.failure(e)
            }
        }
    }

    private fun validateUrl(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        if (url.length > 2048) return false
        val host = HttpUrl.parse(url)?.host ?: return false
        // Reject direct IP addresses (v4/v6)
        if (host.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$""")) || host.startsWith("[") && host.endsWith("]")) return false
        return true
    }

    private fun extractTitle(doc: Document): String {
        val ogTitle = doc.select("meta[property=og:title]").attr("content")
        if (ogTitle.isNotBlank()) return ogTitle.trim()
        val twitterTitle = doc.select("meta[name=twitter:title]").attr("content")
        if (twitterTitle.isNotBlank()) return twitterTitle.trim()
        val tagTitle = doc.title()
        if (tagTitle.isNotBlank()) return tagTitle.trim()
        return ""
    }

    private fun extractImageUrl(doc: Document): String {
        val ogImageMeta = doc.select("meta[property=og:image]").first()
        if (ogImageMeta != null) {
            val ogImage = ogImageMeta.absUrl("content")
            if (ogImage.isNotBlank()) return ogImage
        }
        val twitterImageMeta = doc.select("meta[name=twitter:image]").first()
        if (twitterImageMeta != null) {
            val twitterImage = twitterImageMeta.absUrl("content")
            if (twitterImage.isNotBlank()) return twitterImage
        }
        val firstImg = doc.select("img").first()?.absUrl("src")
        if (firstImg != null && firstImg.isNotBlank()) return firstImg
        return ""
    }

    private fun extractAlternativeTitles(doc: Document): List<String> {
        // Look for alternate title elements or JSON-LD if applicable, but keep it simple for MVP
        return emptyList()
    }

    data class ScrapedMetadata(
        val title: String,
        val imageUrl: String,
        val alternativeTitles: List<String> = emptyList()
    )
}

private const val MAX_SCRAPE_BYTES = 5L * 1024 * 1024

class SizeLimitedSource(delegate: Source, private val maxBytes: Long) : ForwardingSource(delegate) {
    private var totalRead = 0L
    override fun read(sink: Buffer, byteCount: Long): Long {
        val result = super.read(sink, byteCount)
        if (result != -1L) {
            totalRead += result
            if (totalRead > maxBytes) throw IOException("Response size limit of $maxBytes bytes exceeded")
        }
        return result
    }
}
