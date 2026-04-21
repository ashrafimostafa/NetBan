package com.leekleak.trafficlight

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.leekleak.trafficlight.database.databaseModule
import com.leekleak.trafficlight.model.managerModule
import com.leekleak.trafficlight.ui.navigation.navigationModule
import com.leekleak.trafficlight.ui.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import java.util.Locale

class TrafficLightApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@TrafficLightApplication)
            modules(
                systemServiceModule,
                databaseModule,
                managerModule,
                viewModelModule,
                navigationModule
            )
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(wrapContext(base))
    }
}



private fun wrapContext(context: Context): Context {
    val locale = Locale("fa")
    Locale.setDefault(locale)

    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)

    return context.createConfigurationContext(config)
}