package ch.jonathanlemaine.giftmanager

import android.app.Application
import ch.jonathanlemaine.giftmanager.core.config.AppConfig

class GiftManagerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(AppConfig.fromBuildConfig(), this)
        container.startWidgetSync()
    }
}
