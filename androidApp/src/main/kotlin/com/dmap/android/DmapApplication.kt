package com.dmap.android

import android.app.Application
import com.dmap.android.data.DataStoreBackendConfigStore
import com.dmap.config.BackendConfigStore

class DmapApplication : Application() {

    /**
     * Hand-rolled container. Deliberately not a DI framework yet — there are
     * three things in it. Revisit when there are fifteen, not before.
     */
    lateinit var backendConfigStore: BackendConfigStore
        private set

    override fun onCreate() {
        super.onCreate()
        backendConfigStore = DataStoreBackendConfigStore(applicationContext)
    }
}
