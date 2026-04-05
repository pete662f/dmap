package com.dmap.config

data class MapBackendConfig(
    val baseUrl: String,
    val searchBaseUrl: String? = null,
    val routingBaseUrl: String? = null,
) {
    val styleUrl: String = "$baseUrl/styles/osm-liberty/style.json"

    companion object {
        fun fromBaseUrl(
            baseUrl: String,
            searchBaseUrl: String? = null,
            routingBaseUrl: String? = null,
        ): MapBackendConfig {
            val normalizedBaseUrl = baseUrl.trim().removeSuffix("/")
            return MapBackendConfig(
                baseUrl = normalizedBaseUrl,
                searchBaseUrl = searchBaseUrl?.trim()?.removeSuffix("/")?.ifBlank { null },
                routingBaseUrl = routingBaseUrl?.trim()?.removeSuffix("/")?.ifBlank { null },
            )
        }
    }
}
