package com.dmap.app

import android.app.Application
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager

class DmapApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        MapLibre.getInstance(this)
        appContainer = AppContainer(this)

        val cacheSizeBytes = 256L * 1024L * 1024L
        OfflineManager.getInstance(this).setMaximumAmbientCacheSize(cacheSizeBytes, null)
    }
}
