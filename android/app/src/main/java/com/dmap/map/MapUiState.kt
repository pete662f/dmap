package com.dmap.map

import com.dmap.location.LocationAvailabilityState
import com.dmap.location.LocationPermissionState

enum class MapBackendState {
    Loading,
    Ready,
    Unavailable,
}

data class MapUiState(
    val styleUrl: String,
    val backendUrl: String,
    val backendState: MapBackendState = MapBackendState.Loading,
    val locationPermissionState: LocationPermissionState = LocationPermissionState.Unknown,
    val locationAvailabilityState: LocationAvailabilityState = LocationAvailabilityState.Idle,
    val searchUiState: SearchUiState = SearchUiState(),
    val hasEverLoadedStyle: Boolean = false,
    val backendMessage: String? = null,
    val reloadToken: Int = 0,
    val overlayMessage: MapOverlayMessage? = null,
    val isCenteredOnUser: Boolean = false,
)
