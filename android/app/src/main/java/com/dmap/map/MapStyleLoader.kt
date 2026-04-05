package com.dmap.map

import com.dmap.config.MapBackendConfig

class MapStyleLoader(
    private val backendConfig: MapBackendConfig,
) {
    fun styleUrl(): String = backendConfig.styleUrl
    fun backendBaseUrl(): String = backendConfig.baseUrl
}
