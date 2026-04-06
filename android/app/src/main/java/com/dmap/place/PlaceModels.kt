package com.dmap.place

import java.util.Locale

enum class PlaceKind {
    Poi,
    Address,
    Street,
    City,
    Town,
    Village,
    Postcode,
    Region,
    Country,
    Unknown;

    companion object {
        fun fromPhoton(
            type: String?,
            osmKey: String?,
            osmValue: String?,
        ): PlaceKind {
            return when {
                type.equals("house", ignoreCase = true) -> Address
                type.equals("street", ignoreCase = true) -> Street
                type.equals("city", ignoreCase = true) -> City
                type.equals("town", ignoreCase = true) -> Town
                type.equals("village", ignoreCase = true) -> Village
                type.equals("postcode", ignoreCase = true) -> Postcode
                type.equals("county", ignoreCase = true) || type.equals("state", ignoreCase = true) || type.equals("district", ignoreCase = true) -> Region
                type.equals("country", ignoreCase = true) -> Country
                osmKey in setOf("amenity", "shop", "tourism", "leisure", "office", "craft", "historic", "public_transport") -> Poi
                osmValue in setOf("station", "halt", "tram_stop", "bus_stop", "airport", "museum", "hotel", "supermarket") -> Poi
                else -> Unknown
            }
        }
    }
}

data class PlaceSummary(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val latitude: Double,
    val longitude: Double,
    val kind: PlaceKind = PlaceKind.Unknown,
    val categoryHint: String? = null,
) {
    val coordinateLabel: String
        get() = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}

data class SearchResult(
    val place: PlaceSummary,
)

enum class SelectedPlaceType {
    PlaceResult,
    CoordinatePin,
}

enum class SelectedPlaceOrigin {
    Search,
    PoiTap,
    LongPress,
}

data class SelectedPlace(
    val selectionId: Long,
    val place: PlaceSummary,
    val type: SelectedPlaceType,
    val origin: SelectedPlaceOrigin,
)
