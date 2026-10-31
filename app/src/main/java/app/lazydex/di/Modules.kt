package app.lazydex.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lazydex.data.local.LazyDexDatabase
import app.lazydex.data.local.MIGRATION_1_2
import app.lazydex.data.local.MIGRATION_2_3
import app.lazydex.data.local.MIGRATION_3_4
import app.lazydex.data.local.MIGRATION_4_5
import app.lazydex.data.local.ThemePreferences
import app.lazydex.data.local.setupV5TriggersAndSeeds
import app.lazydex.data.repository.MediaRepositoryImpl
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.scraper.MetadataScraper
import app.lazydex.scraper.SafeDns
import app.lazydex.scraper.source.GenericSource
import app.lazydex.scraper.source.SourceRegistry
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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .fallbackToDestructiveMigrationOnDowngrade()
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.setupV5TriggersAndSeeds()
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                android.util.Log.w("LazyDexDatabase", "Room database was wiped due to a destructive migration!")
            }
        })
        .build()
    }

    single { get<LazyDexDatabase>().mediaItemDao() }
    single { get<LazyDexDatabase>().categoryDao() }
    single { get<LazyDexDatabase>().mediaPartDao() }
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
            db = get(),
            dao = get(),
            categoryDao = get(),
            partDao = get(),
            linkDao = get(),
            activityDao = get(),
            outboxDao = get(),
            settingDao = get(),
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
    viewModel { DexViewModel(get(), get()) }
    viewModel { StatisticsViewModel(get()) }
    viewModel { BrowserViewModel(get(), get(), get()) }
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
            localCoversDir = get(named("coversDir")),
            cacheDir = get(named("cacheDir"))
        )
    }
    viewModel { app.lazydex.ui.settings.extensions.ExtensionViewModel(get()) }
}

val appModule = listOf(
    databaseModule,
    repositoryModule,
    preferencesModule,
    scraperModule,
    extensionModule,
    storageModule,
    viewModelModule
)
