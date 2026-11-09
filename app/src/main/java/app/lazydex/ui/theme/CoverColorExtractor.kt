package app.lazydex.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object CoverColorExtractor {

    private const val MAX_CACHE_ENTRIES = 256
    private val cacheLock = Any()
    private val colorCache = object : LinkedHashMap<String, Color>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?): Boolean {
            return size > MAX_CACHE_ENTRIES
        }
    }

    fun clearCache() {
        synchronized(cacheLock) {
            colorCache.clear()
        }
    }

    private fun getCached(key: String): Color? {
        return synchronized(cacheLock) {
            colorCache[key]
        }
    }

    private fun putCached(key: String, color: Color) {
        synchronized(cacheLock) {
            colorCache[key] = color
        }
    }

    /**
     * Decode downscaled bitmap from file path and extract vibrant seed color.
     */
    fun extractSeedColor(imagePath: String): Color? {
        val trimmed = imagePath.trim()
        if (trimmed.isBlank()) return null
        getCached(trimmed)?.let { return it }

        val file = File(trimmed)
        if (!file.exists() || !file.canRead()) return null

        return try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 128)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
            val seedColor = extractFromBitmap(bitmap)
            bitmap.recycle()

            if (seedColor != null) {
                putCached(trimmed, seedColor)
            }
            seedColor
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetch remote cover bytes via OkHttpClient, decode downscaled, and extract vibrant seed color.
     */
    fun extractSeedColorFromUrl(url: String, client: OkHttpClient): Color? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        getCached(trimmed)?.let { return it }

        return try {
            val request = Request.Builder().url(trimmed).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val contentLength = body.contentLength()
                if (contentLength > 10 * 1024 * 1024) return null // Guard against >10MB
                val bytes = body.bytes()
                extractSeedColorFromBytes(bytes, cacheKey = trimmed)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decode downscaled bitmap from byte array and extract vibrant seed color.
     */
    fun extractSeedColorFromBytes(bytes: ByteArray, cacheKey: String? = null): Color? {
        if (bytes.isEmpty()) return null
        cacheKey?.let { getCached(it)?.let { cached -> return cached } }

        return try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 128)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null
            val seedColor = extractFromBitmap(bitmap)
            bitmap.recycle()

            if (seedColor != null && cacheKey != null) {
                putCached(cacheKey, seedColor)
            }
            seedColor
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract vibrant seed color from an in-memory Bitmap.
     * Always ensures downscaling to max 128x128 before generating Palette.
     */
    fun extractFromBitmap(bitmap: Bitmap): Color? {
        val scaled = if (bitmap.width > 128 || bitmap.height > 128) {
            val maxDim = maxOf(bitmap.width, bitmap.height)
            val scale = 128f / maxDim
            val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }
        val palette = Palette.from(scaled).generate()
        if (scaled != bitmap) {
            scaled.recycle()
        }
        return palette.getBestColor()?.let { Color(it) }
    }

    /**
     * Extract vibrant seed color from a Coil/Android Drawable, safely downscaled.
     */
    fun extractFromDrawable(drawable: Drawable, cacheKey: String? = null): Color? {
        cacheKey?.let { key ->
            getCached(key)?.let { return it }
        }

        val bitmap: Bitmap? = when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            else -> {
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
                val bmp = Bitmap.createBitmap(w.coerceAtMost(128), h.coerceAtMost(128), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        }

        if (bitmap == null) return null
        val seedColor = extractFromBitmap(bitmap)

        if (seedColor != null && cacheKey != null) {
            putCached(cacheKey, seedColor)
        }
        return seedColor
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetDim: Int): Int {
        val maxDim = maxOf(width, height)
        var sampleSize = 1
        while (maxDim / (sampleSize * 2) >= targetDim) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Calculate best vibrant color from Palette swatches (Jays2Kings heuristic from Komikku).
     */
    internal fun Palette.getBestColor(): Int? {
        val vibPopulation = vibrantSwatch?.population ?: -1
        val domSat = dominantSwatch?.hsl?.get(1) ?: 0f
        val domLum = dominantSwatch?.hsl?.get(2) ?: -1f
        val mutedPopulation = mutedSwatch?.population ?: -1
        val mutedSat = mutedSwatch?.hsl?.get(1) ?: 0f
        val mutedSatMinAcceptable = if (mutedPopulation > vibPopulation * 3f) 0.1f else 0.25f

        val dominantIsColorful = domSat >= .25f
        val dominantBrightnessJustRight = domLum in 0.2f..0.8f
        val vibrantIsConsiderableBigEnough = vibPopulation >= mutedPopulation * 0.75f
        val mutedIsBig = mutedPopulation > vibPopulation * 1.5f
        val mutedIsNotTooBoring = mutedSat > mutedSatMinAcceptable

        return when {
            dominantIsColorful && dominantBrightnessJustRight -> dominantSwatch
            vibrantIsConsiderableBigEnough -> vibrantSwatch
            mutedIsBig && mutedIsNotTooBoring -> mutedSwatch
            else -> listOfNotNull(vibrantSwatch, lightVibrantSwatch, darkVibrantSwatch)
                .maxByOrNull { if (it === vibrantSwatch) vibPopulation * 3 else it.population }
        }?.rgb
    }
}
