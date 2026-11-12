package app.lazydex.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lazydex.data.local.LazyDexDatabase
import app.lazydex.data.local.ThemePreferences
import app.lazydex.data.local.setupInitialTriggersAndSeeds
import app.lazydex.data.repository.MediaRepositoryImpl
import app.lazydex.data.tracker.anilist.AnilistApi
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.anilist.TokenBucketLimiter
import app.lazydex.data.tracker.service.DelayedTrackingStore
import app.lazydex.data.tracker.sync.AnilistSyncEngine
import app.lazydex.data.tracker.sync.TrackChapter
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.scraper.MetadataScraper
import app.lazydex.scraper.SafeDns
import app.lazydex.scraper.source.GenericSource
import app.lazydex.scraper.source.SourceRegistry
import app.lazydex.ui.browser.BrowserViewModel
import app.lazydex.ui.details.MediaDetailsViewModel
import app.lazydex.ui.dex.DexViewModel
import app.lazydex.ui.history.HistoryViewModel
import app.lazydex.ui.settings.SettingsViewModel
import app.lazydex.ui.statistics.StatisticsViewModel
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import java.time.Duration

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            LazyDexDatabase::class.java,
            "lazydex_db"
        )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigration()
        .fallbackToDestructiveMigrationOnDowngrade()
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.setupInitialTriggersAndSeeds()
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                android.util.Log.w("LazyDexDatabase", "Room database was wiped due to a destructive migration!")
            }
        })
        .build()
    }

    single { get<LazyDexDatabase>().mediaItemDao() }
    // mediaPartDao retired in V6
    single { get<LazyDexDatabase>().mediaLinkDao() }
    single { get<LazyDexDatabase>().activityDao() }
    single { get<LazyDexDatabase>().syncOutboxDao() }
    single { get<LazyDexDatabase>().appSettingDao() }
    single { get<LazyDexDatabase>().extensionRepoDao() }
    single { get<LazyDexDatabase>().installedExtensionDao() }
}

val repositoryModule = module {
    single<MediaRepository> {
        MediaRepositoryImpl(
            context = androidContext(),
            db = get(),
            dao = get(),
            linkDao = get(),
            activityDao = get(),
            outboxDao = get(),
            settingDao = get(),
            localCoversDir = get(named("coversDir")),
            syncEngine = getOrNull(),
            trackChapter = getOrNull(),
            tokenStore = getOrNull()
        )
    }
}

val trackerModule = module {
    single { TokenBucketLimiter() }
    single { DelayedTrackingStore(androidContext()) }
    single {
        AnilistApi(
            okHttpClient = get(),
            tokenStore = get(),
            limiter = get()
        )
    }
    single {
        AnilistSyncEngine(
            client = get(),
            db = get(),
            mediaItemDao = get(),
            mediaLinkDao = get(),
            tokenStore = get()
        )
    }
    single {
        TrackChapter(
            syncEngine = get(),
            tokenStore = get(),
            delayedTrackingStore = get()
        )
    }
}

val preferencesModule = module {
    single { ThemePreferences(androidContext()) }
    single { AnilistTokenStore(androidContext()) }
}

val scraperModule = module {
    single {
        OkHttpClient.Builder()
            .dns(SafeDns())
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(15))
            .writeTimeout(Duration.ofSeconds(10))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
    single { GenericSource(get()) }
    single {
        SourceRegistry(
            listOf(
                get<GenericSource>()
            )
        )
    }
    single { MetadataScraper(get()) }
}

val storageModule = module {
    single(named("cacheDir")) { androidContext().cacheDir }
    single(named("coversDir")) { File(androidContext().filesDir, "covers") }
    single { app.lazydex.data.cache.CoverCache(androidContext()) }
}

val extensionModule = module {
    single { app.lazydex.extension.manifest.ManifestParser() }
    single { app.lazydex.extension.runtime.DexRuntimeEngine(androidContext()) }
    single { app.lazydex.extension.runtime.JsRuntimeEngine(get()) }
    single { app.lazydex.extension.runtime.LuaRuntimeEngine(get()) }
    single { app.lazydex.extension.runtime.WasmRuntimeEngine(get()) }
    single {
        app.lazydex.extension.repository.ExtensionRepository(
            context = androidContext(),
            repoDao = get(),
            installedDao = get(),
            manifestParser = get(),
            okHttpClient = get(),
            dexEngine = get(),
            jsEngine = get(),
            luaEngine = get(),
            wasmEngine = get(),
            sourceRegistry = get()
        )
    }
}

val viewModelModule = module {
    viewModel { DexViewModel(get(), get(), syncEngine = getOrNull()) }
    viewModel { HistoryViewModel(get(), get()) }
    viewModel { StatisticsViewModel(get(), getOrNull()) }
    viewModel { BrowserViewModel(get(), get(), get()) }
    viewModel {
        MediaDetailsViewModel(
            savedStateHandle = get(),
            repository = get(),
            scraper = get(),
            okHttpClient = get(),
            cacheDir = get(named("cacheDir")),
            mediaLinkDao = get(),
            syncEngine = get(),
            tokenStore = get(),
            client = get(),
            themePreferences = get(),
            coverCache = getOrNull(),
        )
    }
    viewModel {
        SettingsViewModel(
            repository = get(),
            themePreferences = get(),
            localCoversDir = get(named("coversDir")),
            cacheDir = get(named("cacheDir")),
            tokenStore = get(),
            syncEngine = get(),
            client = get()
        )
    }
    viewModel { app.lazydex.ui.settings.extensions.ExtensionViewModel(get()) }
}

val appModule = listOf(
    databaseModule,
    repositoryModule,
    preferencesModule,
    trackerModule,
    scraperModule,
    extensionModule,
    storageModule,
    viewModelModule
)
