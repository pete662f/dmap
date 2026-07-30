package com.dmap.config

import kotlinx.serialization.Serializable

/**
 * Backend URLs as RUNTIME state, read from here by both shells.
 *
 * This replaces dmap2's compile-time chain, which resolved four URLs through
 * `.env` → `local.properties` → Gradle property → default and baked the result
 * into `BuildConfig` — about 120 lines of Gradle, two scripts and a recurring
 * "why is my phone showing 10.0.2.2" support question.
 *
 * rev. 3 makes runtime config non-negotiable rather than merely preferable:
 * none of that Gradle machinery has an Xcode equivalent, so a compile-time
 * approach would have to be reimplemented as a build-phase script for iOS. One
 * shared settings screen on each shell is both less code and less pain.
 */
@Serializable
data class BackendConfig(
    val tiles: String = DEFAULT_TILES,
    val places: String = DEFAULT_PLACES,
    val routing: String = "",
    val imagery: String = DEFAULT_IMAGERY,
    val transit: String = "",
    val hazards: String = "",
    val identity: String = "",
) {
    val styleUrl: String get() = "$tiles/styles/osm-liberty/style.json"
    val ortofotoTileUrl: String get() = "$imagery/ortofoto/tiles/{z}/{x}/{y}.jpg"

    fun isConfigured(url: String): Boolean = url.isNotBlank()

    /** Returns the list of problems, empty if fine. Shown in the settings
     *  screen rather than thrown — a half-configured backend is a normal
     *  development state, not an error. */
    fun validate(): List<String> = buildList {
        listOf("tiles" to tiles, "places" to places).forEach { (name, url) ->
            if (url.isBlank()) {
                add("$name backend is not set")
            } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                add("$name backend must start with http:// or https://")
            }
        }
    }

    companion object {
        /**
         * Android emulator reaches the host at 10.0.2.2; the iOS simulator
         * shares the host's network stack and reaches it at localhost. These
         * are only first-launch defaults — the settings screen overrides them,
         * which is how a physical device on the LAN gets pointed at your Mac
         * without a rebuild.
         */
        const val DEFAULT_TILES = "http://localhost:8080"
        const val DEFAULT_PLACES = "http://localhost:8081"
        const val DEFAULT_ROUTING = "http://localhost:8082"
        const val DEFAULT_IMAGERY = "http://localhost:8083"
        const val DEFAULT_TRANSIT = "http://localhost:8084"
        const val DEFAULT_HAZARDS = "http://localhost:8085"
        const val DEFAULT_IDENTITY = "http://localhost:8086"

        const val ANDROID_EMULATOR_HOST = "http://10.0.2.2"
    }
}

/** Persistence seam for [BackendConfig]. DataStore on Android,
 *  NSUserDefaults on iOS — both trivial, neither shared. */
interface BackendConfigStore {
    suspend fun load(): BackendConfig
    suspend fun save(config: BackendConfig)
}

/** In-memory implementation. Used by tests and by the first run of each shell
 *  before a real store is wired up in P0b. */
class InMemoryBackendConfigStore(private var config: BackendConfig = BackendConfig()) :
    BackendConfigStore {
    override suspend fun load(): BackendConfig = config
    override suspend fun save(config: BackendConfig) {
        this.config = config
    }
}
