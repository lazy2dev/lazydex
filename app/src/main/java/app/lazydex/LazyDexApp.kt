package app.lazydex

import android.app.Application
import android.content.Context
import app.lazydex.data.cache.CoverCache
import app.lazydex.data.coil.CoverFetcher
import app.lazydex.data.coil.CoverModelKeyer
import app.lazydex.data.coil.MediaItemKeyer
import app.lazydex.di.appModule
import app.lazydex.extension.repository.ExtensionRepository
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class LazyDexApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        val koinApp = startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LazyDexApp)
            modules(appModule)
        }

        // Eagerly resolve ExtensionRepository so installed sources are loaded and registered immediately
        koinApp.koin.get<ExtensionRepository>()

        val delayedStore = koinApp.koin.get<app.lazydex.data.tracker.service.DelayedTrackingStore>()
        if (delayedStore.getItems().isNotEmpty()) {
            app.lazydex.data.tracker.service.DelayedTrackingUpdateJob.setupTask(this)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val okHttpClient: OkHttpClient = get()
        val coverCache: CoverCache = get()

        return ImageLoader.Builder(this).apply {
            components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                add(CoverFetcher.CoverModelFactory(coverCache, okHttpClient))
                add(CoverFetcher.MediaItemFactory(coverCache, okHttpClient))
                add(CoverModelKeyer())
                add(MediaItemKeyer())
            }
            diskCache(
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            )
            memoryCache(
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            )
            crossfade(false)
            fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            decoderCoroutineContext(Dispatchers.IO.limitedParallelism(4))
        }.build()
    }
}

