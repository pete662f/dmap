package com.dmap.config

data class MapBackendConfig(
    val baseUrl: String,
    val searchBaseUrl: String? = null,
    val routingBaseUrl: String? = null,
    val styleAssetVersion: String = "dev",
) {
    val styleUrl: String = "$baseUrl/styles/osm-liberty/style.json?v=$styleAssetVersion"

    companion object {
        fun fromBaseUrl(
            baseUrl: String,
            searchBaseUrl: String? = null,
            routingBaseUrl: String? = null,
            styleAssetVersion: String = "dev",
        ): MapBackendConfig {
            val normalizedBaseUrl = baseUrl.trim().removeSuffix("/")
            return MapBackendConfig(
                baseUrl = normalizedBaseUrl,
                searchBaseUrl = searchBaseUrl?.trim()?.removeSuffix("/")?.ifBlank { null },
                routingBaseUrl = routingBaseUrl?.trim()?.removeSuffix("/")?.ifBlank { null },
                styleAssetVersion = styleAssetVersion.trim().ifBlank { "dev" },
            )
        }
    }
}
