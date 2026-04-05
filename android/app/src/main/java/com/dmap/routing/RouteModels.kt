package com.dmap.routing

import com.dmap.place.PlaceSummary
import java.util.Locale
import kotlin.math.roundToInt

enum class TravelMode(
    val label: String,
    val valhallaCosting: String,
) {
    Driving("Drive", "auto"),
    Walking("Walk", "pedestrian"),
    Cycling("Bike", "bicycle"),
}

enum class RouteEndpointSource {
    CurrentLocation,
    Search,
    Reverse,
}

data class RouteEndpoint(
    val place: PlaceSummary,
    val source: RouteEndpointSource,
) {
    val title: String
        get() = if (source == RouteEndpointSource.CurrentLocation) {
            "Current location"
        } else {
            place.title
        }

    val subtitle: String?
        get() = place.subtitle
}

data class RouteRequest(
    val origin: RouteEndpoint,
    val destination: RouteEndpoint,
    val travelMode: TravelMode,
)

data class RouteCoordinate(
    val latitude: Double,
    val longitude: Double,
)

data class RoutePath(
    val coordinates: List<RouteCoordinate>,
)

data class RouteSummary(
    val distanceKilometers: Double,
    val durationSeconds: Double,
) {
    val distanceLabel: String
        get() {
            return if (distanceKilometers < 1.0) {
                val meters = (distanceKilometers * 1000.0).roundToInt().coerceAtLeast(1)
                "${meters} m"
            } else if (distanceKilometers < 10.0) {
                String.format(Locale.US, "%.1f km", distanceKilometers)
            } else {
                "${distanceKilometers.roundToInt()} km"
            }
        }

    val durationLabel: String
        get() {
            val totalMinutes = (durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours <= 0 -> "${minutes} min"
                minutes == 0 -> "${hours} hr"
                else -> "${hours} hr ${minutes} min"
            }
        }
}

sealed interface RouteResult {
    data class Success(
        val summary: RouteSummary,
        val path: RoutePath,
    ) : RouteResult

    data object NoRoute : RouteResult

    data class Error(
        val message: String,
    ) : RouteResult
}

enum class RouteStatus {
    Closed,
    Draft,
    Loading,
    Ready,
    NoRoute,
    Error,
}

data class RouteUiState(
    val status: RouteStatus = RouteStatus.Closed,
    val origin: RouteEndpoint? = null,
    val destination: RouteEndpoint? = null,
    val travelMode: TravelMode = TravelMode.Driving,
    val summary: RouteSummary? = null,
    val path: RoutePath? = null,
    val errorMessage: String? = null,
    val fitRequestToken: Long = 0L,
) {
    val plannerVisible: Boolean
        get() = origin != null || destination != null || status != RouteStatus.Closed

    val canRequestRoute: Boolean
        get() = origin != null && destination != null && status != RouteStatus.Loading
}
