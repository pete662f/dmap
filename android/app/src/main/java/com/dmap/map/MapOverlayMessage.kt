package com.dmap.map

enum class MapOverlayTone {
    Info,
    Error,
}

enum class MapOverlaySource {
    Backend,
    Coverage,
    Location,
    Search,
}

data class MapOverlayMessage(
    val id: Long,
    val source: MapOverlaySource,
    val tone: MapOverlayTone,
    val text: String,
    val autoDismissMillis: Long? = 4_000L,
)
