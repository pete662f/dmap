package com.dmap.config

import com.dmap.BuildConfig

object BackendUrlProvider {
    fun fromBuildConfig(): MapBackendConfig {
        return MapBackendConfig.fromBaseUrl(
            baseUrl = BuildConfig.MAP_BACKEND_URL,
            searchBaseUrl = BuildConfig.SEARCH_BACKEND_URL,
            routingBaseUrl = BuildConfig.ROUTING_BACKEND_URL,
        )
    }
}
