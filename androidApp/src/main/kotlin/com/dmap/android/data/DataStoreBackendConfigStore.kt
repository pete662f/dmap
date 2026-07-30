package com.dmap.android.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dmap.config.BackendConfig
import com.dmap.config.BackendConfigStore
import kotlinx.coroutines.flow.first

private val Context.dataStore: androidx.datastore.core.DataStore<Preferences> by
    preferencesDataStore(name = "dmap_settings")

/**
 * Android half of the [BackendConfigStore] port. The iOS half is
 * `UserDefaultsBackendConfigStore` in iosMain — same contract, ~30 lines each,
 * and nothing above them knows which one is running.
 */
class DataStoreBackendConfigStore(private val context: Context) : BackendConfigStore {

    override suspend fun load(): BackendConfig {
        val prefs = context.dataStore.data.first()
        return BackendConfig(
            tiles = prefs[KEY_TILES] ?: defaultForEmulator(BackendConfig.DEFAULT_TILES),
            places = prefs[KEY_PLACES] ?: defaultForEmulator(BackendConfig.DEFAULT_PLACES),
            routing = prefs[KEY_ROUTING] ?: "",
            imagery = prefs[KEY_IMAGERY] ?: defaultForEmulator(BackendConfig.DEFAULT_IMAGERY),
            transit = prefs[KEY_TRANSIT] ?: "",
            hazards = prefs[KEY_HAZARDS] ?: "",
            identity = prefs[KEY_IDENTITY] ?: "",
        )
    }

    override suspend fun save(config: BackendConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TILES] = config.tiles
            prefs[KEY_PLACES] = config.places
            prefs[KEY_ROUTING] = config.routing
            prefs[KEY_IMAGERY] = config.imagery
            prefs[KEY_TRANSIT] = config.transit
            prefs[KEY_HAZARDS] = config.hazards
            prefs[KEY_IDENTITY] = config.identity
        }
    }

    /** The emulator reaches the host at 10.0.2.2, not localhost. Only a
     *  first-launch default — the settings screen overrides it, which is how a
     *  physical device gets pointed at your Mac without a rebuild. */
    private fun defaultForEmulator(localhostUrl: String): String =
        localhostUrl.replace("http://localhost", BackendConfig.ANDROID_EMULATOR_HOST)

    private companion object {
        val KEY_TILES = stringPreferencesKey("backend_tiles")
        val KEY_PLACES = stringPreferencesKey("backend_places")
        val KEY_ROUTING = stringPreferencesKey("backend_routing")
        val KEY_IMAGERY = stringPreferencesKey("backend_imagery")
        val KEY_TRANSIT = stringPreferencesKey("backend_transit")
        val KEY_HAZARDS = stringPreferencesKey("backend_hazards")
        val KEY_IDENTITY = stringPreferencesKey("backend_identity")
    }
}
