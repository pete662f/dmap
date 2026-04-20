package com.dmap.config

data class MapBackendConfig(
    val baseUrl: String,
    val searchBaseUrl: String? = null,
    val routingBaseUrl: String? = null,
    val imageryBaseUrl: String? = null,
) {
    val styleUrl: String = "$baseUrl/styles/osm-liberty/style.json"
    val ortofotoTileUrl: String? = imageryBaseUrl?.let { "$it/ortofoto/tiles/{z}/{x}/{y}.jpg" }

    companion object {
        fun fromBaseUrl(
            baseUrl: String,
            searchBaseUrl: String? = null,
            routingBaseUrl: String? = null,
            imageryBaseUrl: String? = null,
        ): MapBackendConfig {
            val normalizedBaseUrl = baseUrl.trim().removeSuffix("/")
            require(normalizedBaseUrl.isNotBlank()) {
                "Map backend URL must not be blank."
            }
            require(normalizedBaseUrl.startsWith("http://") || normalizedBaseUrl.startsWith("https://")) {
                "Map backend URL must start with http:// or https://."
            }
            return MapBackendConfig(
                baseUrl = normalizedBaseUrl,
                searchBaseUrl = searchBaseUrl?.trim()?.removeSuffix("/")?.ifBlank { null },
                routingBaseUrl = routingBaseUrl?.trim()?.removeSuffix("/")?.ifBlank { null },
                imageryBaseUrl = imageryBaseUrl?.trim()?.removeSuffix("/")?.ifBlank { null },
            )
        }
    }
}
