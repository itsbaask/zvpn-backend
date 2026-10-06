package com.zvpn

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import com.zvpn.core.XrayController
import com.zvpn.core.platform.LocaleManager
import com.zvpn.data.settings.SettingsRepository
import com.zvpn.di.AppContainer

class ZVpnApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrap(base, SettingsRepository.readLanguageTag(base)))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.reassert(SettingsRepository.readLanguageTag(this))
    }

    override fun onCreate() {
        super.onCreate()

        container = AppContainer(this)

        XrayController.warmUp(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
}
