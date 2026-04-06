package com.dmap.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dmap.app.AppContainer
import com.dmap.location.LocationAvailabilityState
import com.dmap.location.LocationPermissionState
import com.dmap.location.LocateMeResult
import com.dmap.place.PlaceKind
import com.dmap.place.PlaceSummary
import com.dmap.place.SelectedPlace
import com.dmap.place.SelectedPlaceOrigin
import com.dmap.place.SearchResult
import com.dmap.services.search.SearchBias
import com.dmap.services.search.SearchService
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapViewModel(
    appContainer: AppContainer,
) : ViewModel() {
    private var nextMessageId = 1L
    private var nextSelectionId = 1L

    private val searchService: SearchService = appContainer.searchService
    private val searchQuery = MutableStateFlow("")
    private val searchBias = MutableStateFlow<SearchBias?>(null)

    private val _uiState = MutableStateFlow(
        MapUiState(
            styleUrl = appContainer.mapStyleLoader.styleUrl(),
            backendUrl = appContainer.mapStyleLoader.backendBaseUrl(),
        ),
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        observeSearch()
    }

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

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
        _uiState.update { state ->
            state.copy(
                searchUiState = state.searchUiState.copy(
                    query = query,
                    status = if (query.trim().length < 2) SearchStatus.Idle else state.searchUiState.status,
                    results = if (query.isBlank()) emptyList() else state.searchUiState.results,
                    errorMessage = null,
                ),
            )
        }
    }

    fun updateSearchBias(
        latitude: Double,
        longitude: Double,
        zoom: Double,
    ) {
        searchBias.value = SearchBias(
            latitude = latitude,
            longitude = longitude,
            zoom = zoom.toInt(),
        )
    }

    fun selectSearchResult(result: SearchResult) {
        searchQuery.value = ""
        _uiState.update { state ->
            state.copy(
                searchUiState = state.searchUiState.copy(
                    query = "",
                    status = SearchStatus.Idle,
                    results = emptyList(),
                    errorMessage = null,
                    selectedPlace = SelectedPlace(
                        selectionId = nextSelectionId++,
                        place = result.place,
                        origin = SelectedPlaceOrigin.Search,
                    ),
                ),
                overlayMessage = state.overlayMessage.takeUnless { it?.source == MapOverlaySource.Search },
            )
        }
    }

    fun clearSelectedPlace() {
        _uiState.update { state ->
            state.copy(
                searchUiState = state.searchUiState.copy(selectedPlace = null),
            )
        }
    }

    fun reverseGeocodeSelection(
        longitude: Double,
        latitude: Double,
    ) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    overlayMessage = newMessage(
                        source = MapOverlaySource.Search,
                        tone = MapOverlayTone.Info,
                        text = "Looking up this spot…",
                        autoDismissMillis = null,
                    ),
                )
            }

            val selection = runCatching {
                searchService.reverseGeocode(
                    longitude = longitude,
                    latitude = latitude,
                )
            }.fold(
                onSuccess = { result ->
                    when {
                        result != null -> result.place to null
                        else -> droppedPin(latitude, longitude) to newMessage(
                            source = MapOverlaySource.Search,
                            tone = MapOverlayTone.Info,
                            text = "No nearby place found. Showing a dropped pin.",
                            autoDismissMillis = 3_500L,
                        )
                    }
                },
                onFailure = {
                    droppedPin(latitude, longitude) to newMessage(
                        source = MapOverlaySource.Search,
                        tone = MapOverlayTone.Error,
                        text = "Search backend is unavailable. Showing a dropped pin.",
                        autoDismissMillis = 4_500L,
                    )
                },
            )

            _uiState.update { state ->
                state.copy(
                    searchUiState = state.searchUiState.copy(
                        selectedPlace = SelectedPlace(
                            selectionId = nextSelectionId++,
                            place = selection.first,
                            origin = SelectedPlaceOrigin.Reverse,
                        ),
                    ),
                    overlayMessage = selection.second,
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

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.launch {
            combine(searchQuery, searchBias) { query, bias ->
                query.trim() to bias
            }.debounce(300L).collectLatest { (query, bias) ->
                if (query.length < 2) {
                    _uiState.update { state ->
                        state.copy(
                            searchUiState = state.searchUiState.copy(
                                status = SearchStatus.Idle,
                                results = emptyList(),
                                errorMessage = null,
                            ),
                        )
                    }
                    return@collectLatest
                }

                _uiState.update { state ->
                    state.copy(
                        searchUiState = state.searchUiState.copy(
                            status = SearchStatus.Loading,
                            results = emptyList(),
                            errorMessage = null,
                        ),
                    )
                }

                runCatching {
                    searchService.search(
                        query = query,
                        bias = bias,
                        limit = 8,
                    )
                }.onSuccess { results ->
                    _uiState.update { state ->
                        state.copy(
                            searchUiState = state.searchUiState.copy(
                                status = if (results.isEmpty()) SearchStatus.Empty else SearchStatus.Results,
                                results = results,
                                errorMessage = null,
                            ),
                        )
                    }
                }.onFailure {
                    _uiState.update { state ->
                        state.copy(
                            searchUiState = state.searchUiState.copy(
                                status = SearchStatus.Error,
                                results = emptyList(),
                                errorMessage = "Search is temporarily unavailable.",
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun droppedPin(
        latitude: Double,
        longitude: Double,
    ): PlaceSummary {
        return PlaceSummary(
            id = "drop:${latitude},${longitude}",
            title = "Dropped pin",
            subtitle = null,
            latitude = latitude,
            longitude = longitude,
            kind = PlaceKind.Unknown,
        )
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
