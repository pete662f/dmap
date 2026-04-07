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
import com.dmap.place.SelectedPlaceType
import com.dmap.place.SearchResult
import com.dmap.services.search.SearchBias
import com.dmap.services.search.SearchService
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
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
    private val searchService: SearchService,
    styleUrl: String,
    backendUrl: String,
) : ViewModel() {
    private var nextMessageId = 1L
    private var nextSelectionId = 1L
    private var latestCameraLatitude: Double? = null
    private var latestCameraLongitude: Double? = null
    private var latestUserLatitude: Double? = null
    private var latestUserLongitude: Double? = null

    private val searchQuery = MutableStateFlow("")
    private val searchBias = MutableStateFlow<SearchBias?>(null)

    private val _uiState = MutableStateFlow(
        MapUiState(
            styleUrl = styleUrl,
            backendUrl = backendUrl,
        ),
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    constructor(appContainer: AppContainer) : this(
        searchService = appContainer.searchService,
        styleUrl = appContainer.mapStyleLoader.styleUrl(),
        backendUrl = appContainer.mapStyleLoader.backendBaseUrl(),
    )

    init {
        observeSearch()
    }

    fun setLocationPermission(granted: Boolean) {
        if (!granted) {
            clearUserLocationSample()
        }
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
                isCenteredOnUser = if (granted) state.isCenteredOnUser else false,
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

    fun onCameraIdle(
        latitude: Double,
        longitude: Double,
        zoom: Double,
    ) {
        latestCameraLatitude = latitude
        latestCameraLongitude = longitude
        searchBias.value = SearchBias(
            latitude = latitude,
            longitude = longitude,
            zoom = zoom.toInt(),
        )
        recomputeCenteredOnUser()
    }

    fun onUserLocationSample(
        latitude: Double,
        longitude: Double,
    ) {
        latestUserLatitude = latitude
        latestUserLongitude = longitude
        _uiState.update { state ->
            state.copy(
                locationAvailabilityState = LocationAvailabilityState.Available,
                overlayMessage = state.overlayMessage.takeUnless { it?.source == MapOverlaySource.Location },
            )
        }
        recomputeCenteredOnUser()
    }

    fun onUserLocationUnavailable() {
        clearUserLocationSample()
        _uiState.update { state ->
            if (!state.isCenteredOnUser) {
                state
            } else {
                state.copy(isCenteredOnUser = false)
            }
        }
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
                        type = SelectedPlaceType.PlaceResult,
                        origin = SelectedPlaceOrigin.Search,
                    ),
                ),
                overlayMessage = state.overlayMessage.takeUnless { it?.source == MapOverlaySource.Search },
            )
        }
    }

    fun selectRenderedPoi(place: PlaceSummary) {
        searchQuery.value = ""
        val selectionId = nextSelectionId++
        _uiState.update { state ->
            state.copy(
                searchUiState = state.searchUiState.copy(
                    query = "",
                    status = SearchStatus.Idle,
                    results = emptyList(),
                    errorMessage = null,
                    selectedPlace = SelectedPlace(
                        selectionId = selectionId,
                        place = place,
                        type = SelectedPlaceType.PlaceResult,
                        origin = SelectedPlaceOrigin.PoiTap,
                    ),
                    isEnrichingPlace = true,
                ),
                overlayMessage = state.overlayMessage.takeUnless { it?.source == MapOverlaySource.Search },
            )
        }

        viewModelScope.launch {
            val enrichedPlace = runCatching {
                searchService.reverseGeocode(
                    longitude = place.longitude,
                    latitude = place.latitude,
                )
            }.getOrNull()?.place?.takeIf { resultPlace ->
                distanceMeters(
                    startLatitude = place.latitude,
                    startLongitude = place.longitude,
                    endLatitude = resultPlace.latitude,
                    endLongitude = resultPlace.longitude,
                ) <= LONG_PRESS_LABEL_MAX_DISTANCE_METERS
            }?.let { labelSource ->
                place.copy(
                    title = if (place.title == "Selected place") labelSource.title else place.title,
                    subtitle = labelSource.subtitle ?: place.subtitle,
                    kind = if (place.kind == PlaceKind.Unknown) labelSource.kind else place.kind,
                    categoryHint = place.categoryHint ?: labelSource.categoryHint,
                )
            }

            _uiState.update { state ->
                val currentSelection = state.searchUiState.selectedPlace
                if (
                    currentSelection?.selectionId != selectionId ||
                    currentSelection.origin != SelectedPlaceOrigin.PoiTap
                ) {
                    return@update state
                }

                state.copy(
                    searchUiState = state.searchUiState.copy(
                        selectedPlace = if (enrichedPlace != null) {
                            currentSelection.copy(place = enrichedPlace)
                        } else {
                            currentSelection
                        },
                        isEnrichingPlace = false,
                    ),
                )
            }
        }
    }

    fun clearSelectedPlace() {
        _uiState.update { state ->
            state.copy(
                searchUiState = state.searchUiState.copy(
                    selectedPlace = null,
                    isEnrichingPlace = false,
                ),
            )
        }
    }

    fun selectCoordinateFromLongPress(
        longitude: Double,
        latitude: Double,
    ) {
        val selectionId = nextSelectionId++
        val initialSelection = SelectedPlace(
            selectionId = selectionId,
            place = coordinateSummary(latitude, longitude, labelSource = null),
            type = SelectedPlaceType.CoordinatePin,
            origin = SelectedPlaceOrigin.LongPress,
        )

        _uiState.update { state ->
            state.copy(
                searchUiState = state.searchUiState.copy(
                    selectedPlace = initialSelection,
                    isEnrichingPlace = true,
                ),
                overlayMessage = newMessage(
                    source = MapOverlaySource.Search,
                    tone = MapOverlayTone.Info,
                    text = "Looking up this spot…",
                    autoDismissMillis = null,
                ),
            )
        }

        viewModelScope.launch {
            val selection = runCatching {
                searchService.reverseGeocode(
                    longitude = longitude,
                    latitude = latitude,
                )
            }.fold(
                onSuccess = { result ->
                    when {
                        result != null -> {
                            val distanceMeters = distanceMeters(
                                startLatitude = latitude,
                                startLongitude = longitude,
                                endLatitude = result.place.latitude,
                                endLongitude = result.place.longitude,
                            )
                            if (distanceMeters <= LONG_PRESS_LABEL_MAX_DISTANCE_METERS) {
                                coordinateSummary(
                                    latitude = latitude,
                                    longitude = longitude,
                                    labelSource = result.place,
                                ) to null
                            } else {
                                coordinateSummary(
                                    latitude = latitude,
                                    longitude = longitude,
                                    labelSource = null,
                                ) to newMessage(
                                    source = MapOverlaySource.Search,
                                    tone = MapOverlayTone.Info,
                                    text = "No nearby place found. Showing a dropped pin.",
                                    autoDismissMillis = 3_500L,
                                )
                            }
                        }
                        else -> coordinateSummary(
                            latitude = latitude,
                            longitude = longitude,
                            labelSource = null,
                        ) to newMessage(
                            source = MapOverlaySource.Search,
                            tone = MapOverlayTone.Info,
                            text = "No nearby place found. Showing a dropped pin.",
                            autoDismissMillis = 3_500L,
                        )
                    }
                },
                onFailure = {
                    coordinateSummary(
                        latitude = latitude,
                        longitude = longitude,
                        labelSource = null,
                    ) to newMessage(
                        source = MapOverlaySource.Search,
                        tone = MapOverlayTone.Error,
                        text = "Search backend is unavailable. Showing a dropped pin.",
                        autoDismissMillis = 4_500L,
                    )
                },
            )

            _uiState.update { state ->
                val currentSelection = state.searchUiState.selectedPlace
                if (currentSelection?.selectionId != selectionId) {
                    return@update state
                }

                state.copy(
                    searchUiState = state.searchUiState.copy(
                        selectedPlace = SelectedPlace(
                            selectionId = selectionId,
                            place = selection.first,
                            type = SelectedPlaceType.CoordinatePin,
                            origin = SelectedPlaceOrigin.LongPress,
                        ),
                        isEnrichingPlace = false,
                    ),
                    overlayMessage = selection.second
                        ?: state.overlayMessage.takeUnless { it?.source == MapOverlaySource.Search },
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

    private fun coordinateSummary(
        latitude: Double,
        longitude: Double,
        labelSource: PlaceSummary?,
    ): PlaceSummary {
        return PlaceSummary(
            id = "coord:${latitude},${longitude}",
            title = labelSource?.title ?: "Dropped pin",
            subtitle = labelSource?.subtitle,
            latitude = latitude,
            longitude = longitude,
            kind = labelSource?.kind ?: PlaceKind.Unknown,
            categoryHint = labelSource?.categoryHint,
        )
    }

    private fun distanceMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
    ): Double {
        val latitudeDelta = Math.toRadians(endLatitude - startLatitude)
        val longitudeDelta = Math.toRadians(endLongitude - startLongitude)
        val startLatitudeRadians = Math.toRadians(startLatitude)
        val endLatitudeRadians = Math.toRadians(endLatitude)

        val haversine = sin(latitudeDelta / 2).pow(2) +
            cos(startLatitudeRadians) * cos(endLatitudeRadians) * sin(longitudeDelta / 2).pow(2)
        val angularDistance = 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
        return EARTH_RADIUS_METERS * angularDistance
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

    private fun clearUserLocationSample() {
        latestUserLatitude = null
        latestUserLongitude = null
    }

    private fun recomputeCenteredOnUser() {
        val cameraLatitude = latestCameraLatitude
        val cameraLongitude = latestCameraLongitude
        val userLatitude = latestUserLatitude
        val userLongitude = latestUserLongitude

        val isCenteredOnUser = if (
            cameraLatitude != null &&
            cameraLongitude != null &&
            userLatitude != null &&
            userLongitude != null
        ) {
            MapCenteringEvaluator.evaluate(
                wasCenteredOnUser = _uiState.value.isCenteredOnUser,
                cameraLatitude = cameraLatitude,
                cameraLongitude = cameraLongitude,
                userLatitude = userLatitude,
                userLongitude = userLongitude,
            )
        } else {
            false
        }

        _uiState.update { state ->
            if (state.isCenteredOnUser == isCenteredOnUser) {
                state
            } else {
                state.copy(isCenteredOnUser = isCenteredOnUser)
            }
        }
    }

    companion object {
        private const val LONG_PRESS_LABEL_MAX_DISTANCE_METERS = 25.0
        private const val EARTH_RADIUS_METERS = 6_371_000.0

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
