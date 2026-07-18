package app.lazydex.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lazydex.data.local.LazyDexDatabase
import app.lazydex.data.local.ThemePreferences
import app.lazydex.data.repository.MediaRepositoryImpl
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.scraper.MetadataScraper
import app.lazydex.scraper.SafeDns
import app.lazydex.ui.addedit.UnifiedAddEditViewModel
import app.lazydex.ui.dex.DexViewModel
import app.lazydex.ui.settings.SettingsViewModel
import app.lazydex.ui.statistics.StatisticsViewModel
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
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
    single { MetadataScraper(get()) }
}

val storageModule = module {
    single(named("cacheDir")) { androidContext().cacheDir }
    single(named("coversDir")) { File(androidContext().filesDir, "covers") }
}

val viewModelModule = module {
    viewModel { DexViewModel(get()) }
    viewModel { StatisticsViewModel(get()) }
    viewModel {
        UnifiedAddEditViewModel(
            savedStateHandle = get(),
            repository = get(),
            scraper = get(),
            okHttpClient = get(),
            cacheDir = get(named("cacheDir")),
            localCoversDir = get(named("coversDir"))
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
