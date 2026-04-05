package com.dmap.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dmap.app.AppContainer
import com.dmap.location.LocationAvailabilityState
import com.dmap.location.LocationPermissionState
import com.dmap.location.LocateMeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MapViewModel(
    appContainer: AppContainer,
) : ViewModel() {
    private var nextMessageId = 1L

    private val _uiState = MutableStateFlow(
        MapUiState(
            styleUrl = appContainer.mapStyleLoader.styleUrl(),
            backendUrl = appContainer.mapStyleLoader.backendBaseUrl(),
        ),
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun setLocationPermission(granted: Boolean) {
        _uiState.update { state ->
            state.copy(
                locationPermissionState = if (granted) {
                    LocationPermissionState.Granted
                } else {
                    LocationPermissionState.Denied
                },
                locationAvailabilityState = if (granted) {
                    if (state.locationAvailabilityState == LocationAvailabilityState.Available) {
                        LocationAvailabilityState.Available
                    } else {
                        LocationAvailabilityState.Locating
                    }
                } else {
                    LocationAvailabilityState.Idle
                },
                overlayMessage = state.overlayMessage.takeUnless { it?.source == MapOverlaySource.Location },
            )
        }
    }

    fun onStyleLoading() {
        _uiState.update { state ->
            state.copy(
                backendState = if (state.hasEverLoadedStyle) state.backendState else MapBackendState.Loading,
                backendMessage = null,
                overlayMessage = state.overlayMessage.takeUnless { it?.source == MapOverlaySource.Backend },
            )
        }
    }

    fun onStyleLoaded() {
        _uiState.update { state ->
            state.copy(
                backendState = MapBackendState.Ready,
                hasEverLoadedStyle = true,
                backendMessage = null,
                overlayMessage = state.overlayMessage.takeUnless { it?.source == MapOverlaySource.Backend },
            )
        }
    }

    fun onBackendLoadFailed(message: String?) {
        _uiState.update { state ->
            if (state.hasEverLoadedStyle) {
                state.copy(
                    backendMessage = message ?: "Tile server became unavailable.",
                    overlayMessage = newMessage(
                        source = MapOverlaySource.Backend,
                        tone = MapOverlayTone.Error,
                        text = message ?: "Map tiles could not be refreshed from the self-hosted backend.",
                        autoDismissMillis = 4_500L,
                    ),
                )
            } else {
                state.copy(
                    backendState = MapBackendState.Unavailable,
                    backendMessage = message ?: "Could not load the self-hosted map style.",
                    overlayMessage = null,
                )
            }
        }
    }

    fun setLocationAvailability(state: LocationAvailabilityState) {
        _uiState.update { current ->
            if (current.locationAvailabilityState == state) return@update current

            current.copy(
                locationAvailabilityState = state,
                overlayMessage = if (state == LocationAvailabilityState.Available) {
                    current.overlayMessage.takeUnless { it?.source == MapOverlaySource.Location }
                } else {
                    current.overlayMessage
                },
            )
        }
    }

    fun onLocateMeResult(result: LocateMeResult) {
        _uiState.update { state ->
            when (result) {
                LocateMeResult.PermissionRequired -> state
                LocateMeResult.WaitingForFix -> state.copy(
                    locationAvailabilityState = LocationAvailabilityState.Locating,
                    overlayMessage = newMessage(
                        source = MapOverlaySource.Location,
                        tone = MapOverlayTone.Info,
                        text = "Finding your location…",
                        autoDismissMillis = 3_000L,
                    ),
                )
                LocateMeResult.Centered -> state.copy(
                    locationAvailabilityState = LocationAvailabilityState.Available,
                    overlayMessage = state.overlayMessage.takeUnless { it?.source == MapOverlaySource.Location },
                )
                LocateMeResult.Unavailable -> state.copy(
                    locationAvailabilityState = LocationAvailabilityState.Unavailable,
                    overlayMessage = newMessage(
                        source = MapOverlaySource.Location,
                        tone = MapOverlayTone.Info,
                        text = "Current location is unavailable right now.",
                        autoDismissMillis = 4_000L,
                    ),
                )
            }
        }
    }

    fun dismissOverlayMessage(messageId: Long) {
        _uiState.update { state ->
            if (state.overlayMessage?.id == messageId) {
                state.copy(overlayMessage = null)
            } else {
                state
            }
        }
    }

    fun retry() {
        _uiState.update { state ->
            state.copy(
                backendState = if (state.hasEverLoadedStyle) MapBackendState.Ready else MapBackendState.Loading,
                backendMessage = null,
                overlayMessage = state.overlayMessage.takeUnless { it?.source == MapOverlaySource.Backend },
                reloadToken = state.reloadToken + 1,
            )
        }
    }

    private fun newMessage(
        source: MapOverlaySource,
        tone: MapOverlayTone,
        text: String,
        autoDismissMillis: Long?,
    ): MapOverlayMessage {
        return MapOverlayMessage(
            id = nextMessageId++,
            source = source,
            tone = tone,
            text = text,
            autoDismissMillis = autoDismissMillis,
        )
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MapViewModel(appContainer) as T
                }
            }
        }
    }
}
