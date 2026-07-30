package com.dmap.config

import platform.Foundation.NSUserDefaults

/**
 * iOS half of the [BackendConfigStore] port, mirroring
 * `DataStoreBackendConfigStore` on Android. Note there is no 10.0.2.2 special
 * case: the iOS simulator shares the host network stack, so localhost already
 * works.
 */
class UserDefaultsBackendConfigStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : BackendConfigStore {

    override suspend fun load(): BackendConfig = BackendConfig(
        tiles = defaults.stringForKey(KEY_TILES) ?: BackendConfig.DEFAULT_TILES,
        places = defaults.stringForKey(KEY_PLACES) ?: BackendConfig.DEFAULT_PLACES,
        routing = defaults.stringForKey(KEY_ROUTING) ?: "",
        imagery = defaults.stringForKey(KEY_IMAGERY) ?: BackendConfig.DEFAULT_IMAGERY,
        transit = defaults.stringForKey(KEY_TRANSIT) ?: "",
        hazards = defaults.stringForKey(KEY_HAZARDS) ?: "",
        identity = defaults.stringForKey(KEY_IDENTITY) ?: "",
    )

    override suspend fun save(config: BackendConfig) {
        defaults.setObject(config.tiles, KEY_TILES)
        defaults.setObject(config.places, KEY_PLACES)
        defaults.setObject(config.routing, KEY_ROUTING)
        defaults.setObject(config.imagery, KEY_IMAGERY)
        defaults.setObject(config.transit, KEY_TRANSIT)
        defaults.setObject(config.hazards, KEY_HAZARDS)
        defaults.setObject(config.identity, KEY_IDENTITY)
    }

    private companion object {
        const val KEY_TILES = "backend_tiles"
        const val KEY_PLACES = "backend_places"
        const val KEY_ROUTING = "backend_routing"
        const val KEY_IMAGERY = "backend_imagery"
        const val KEY_TRANSIT = "backend_transit"
        const val KEY_HAZARDS = "backend_hazards"
        const val KEY_IDENTITY = "backend_identity"
    }
}
