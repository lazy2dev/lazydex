package app.lazydex.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lazydex.data.local.LazyDexDatabase
import app.lazydex.data.local.MIGRATION_1_2
import app.lazydex.data.local.MIGRATION_2_3
import app.lazydex.data.local.ThemePreferences
import app.lazydex.data.repository.MediaRepositoryImpl
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.scraper.MetadataScraper
import app.lazydex.scraper.SafeDns
import app.lazydex.scraper.source.AniListSource
import app.lazydex.scraper.source.GenericSource
import app.lazydex.scraper.source.MangaDexSource
import app.lazydex.scraper.source.NovelUpdatesSource
import app.lazydex.scraper.source.RoyalRoadSource
import app.lazydex.scraper.source.SourceRegistry
import app.lazydex.scraper.source.WtrLabSource
import app.lazydex.ui.addedit.UnifiedAddEditViewModel
import app.lazydex.ui.browser.BrowserViewModel
import app.lazydex.ui.dex.DexViewModel
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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .fallbackToDestructiveMigration()
        .addCallback(object : RoomDatabase.Callback() {
            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                android.util.Log.w("LazyDexDatabase", "Room database was wiped due to a destructive migration!")
            }
        })
        .build()
    }

    single { get<LazyDexDatabase>().mediaItemDao() }
}

val repositoryModule = module {
    single<MediaRepository> {
        MediaRepositoryImpl(
            dao = get(),
            localCoversDir = get(named("coversDir"))
        )
    }
}

val preferencesModule = module {
    single { ThemePreferences(androidContext()) }
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
    single { WtrLabSource(get()) }
    single { NovelUpdatesSource(get()) }
    single { RoyalRoadSource(get()) }
    single { MangaDexSource(get()) }
    single { AniListSource(get()) }
    single { GenericSource(get()) }
    single {
        SourceRegistry(
            listOf(
                get<WtrLabSource>(),
                get<NovelUpdatesSource>(),
                get<RoyalRoadSource>(),
                get<MangaDexSource>(),
                get<AniListSource>(),
                get<GenericSource>()
            )
        )
    }
    single { MetadataScraper(get()) }
}

val storageModule = module {
    single(named("cacheDir")) { androidContext().cacheDir }
    single(named("coversDir")) { File(androidContext().filesDir, "covers") }
}

val viewModelModule = module {
    viewModel { DexViewModel(get()) }
    viewModel { StatisticsViewModel(get()) }
    viewModel { BrowserViewModel(get()) }
    viewModel {
        UnifiedAddEditViewModel(
            savedStateHandle = get(),
            repository = get(),
            scraper = get(),
            okHttpClient = get(),
            cacheDir = get(named("cacheDir"))
        )
    }
    viewModel {
        SettingsViewModel(
            repository = get(),
            themePreferences = get(),
            localCoversDir = get(named("coversDir"))
        )
    }
}

val appModule = listOf(
    databaseModule,
    repositoryModule,
    preferencesModule,
    scraperModule,
    storageModule,
    viewModelModule
)
