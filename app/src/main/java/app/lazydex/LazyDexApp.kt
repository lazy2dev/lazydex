package app.lazydex

import android.app.Application
import app.lazydex.di.appModule
import app.lazydex.extension.repository.ExtensionRepository
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class LazyDexApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val koinApp = startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LazyDexApp)
            modules(appModule)
        }

        // Eagerly resolve ExtensionRepository so installed sources are loaded and registered immediately
        koinApp.koin.get<ExtensionRepository>()
    }
}
