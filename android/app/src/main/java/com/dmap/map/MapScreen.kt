package com.dmap.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmap.R
import com.dmap.app.AppContainer
import com.dmap.location.LocationAvailabilityState
import com.dmap.location.LocationPermissionState
import com.dmap.location.LocateMeResult
import com.dmap.place.SearchResult
import com.dmap.place.SelectedPlace
import com.dmap.place.SelectedPlaceOrigin
import com.dmap.routing.RouteStatus
import com.dmap.routing.RouteUiState
import com.dmap.routing.TravelMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun MapScreen(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val viewModel: MapViewModel = viewModel(factory = MapViewModel.factory(appContainer))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapView = rememberMapViewWithLifecycle(lifecycleOwner)
    val mapPresentation = remember { MapPresentationConfig.denmark() }
    val markerController = remember(context) { SelectedPlaceMarkerController(context) }
    val routeController = remember { RouteOverlayController() }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentStyle by remember { mutableStateOf<Style?>(null) }
    var searchFocused by remember { mutableStateOf(false) }

    val routeFitPaddingPx = with(density) { mapPresentation.routeFitPaddingDp.dp.roundToPx() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setLocationPermission(granted)
        if (granted) {
            val map = mapLibreMap
            val style = currentStyle
            if (map != null && style != null) {
                appContainer.locationController.enableLocation(map, style)
                viewModel.setLocationAvailability(
                    appContainer.locationController.locationAvailability(map),
                )
                appContainer.locationController.lastKnownLocation(map)?.let { location ->
                    viewModel.updateCurrentLocation(location.latitude, location.longitude)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setLocationPermission(appContainer.locationController.hasLocationPermission())
    }

    DisposableEffect(mapView) {
        val onFail = MapView.OnDidFailLoadingMapListener { errorMessage ->
            viewModel.onBackendLoadFailed(errorMessage)
        }
        mapView.addOnDidFailLoadingMapListener(onFail)
        onDispose {
            mapView.removeOnDidFailLoadingMapListener(onFail)
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            val initialTarget = mapPresentation.defaultCamera.target ?: LatLng(56.1725, 10.0938)
            mapLibreMap = map
            map.uiSettings.setRotateGesturesEnabled(true)
            map.uiSettings.setTiltGesturesEnabled(false)
            map.uiSettings.setCompassEnabled(true)
            map.setMinZoomPreference(mapPresentation.minZoom)
            map.setMaxZoomPreference(mapPresentation.maxZoom)
            map.setLatLngBoundsForCameraTarget(mapPresentation.cameraBounds)
            map.cameraPosition = mapPresentation.defaultCamera
            viewModel.updateSearchBias(
                latitude = initialTarget.latitude,
                longitude = initialTarget.longitude,
                zoom = mapPresentation.defaultCamera.zoom,
            )
        }
    }

    DisposableEffect(mapLibreMap) {
        val map = mapLibreMap ?: return@DisposableEffect onDispose { }

        val cameraListener = MapLibreMap.OnCameraIdleListener {
            val camera = map.cameraPosition
            val target = camera.target ?: return@OnCameraIdleListener
            viewModel.updateSearchBias(
                latitude = target.latitude,
                longitude = target.longitude,
                zoom = camera.zoom,
            )
        }
        val longClickListener = MapLibreMap.OnMapLongClickListener { point ->
            focusManager.clearFocus(force = true)
            viewModel.reverseGeocodeSelection(
                longitude = point.longitude,
                latitude = point.latitude,
            )
            true
        }

        map.addOnCameraIdleListener(cameraListener)
        map.addOnMapLongClickListener(longClickListener)

        onDispose {
            map.removeOnCameraIdleListener(cameraListener)
            map.removeOnMapLongClickListener(longClickListener)
        }
    }

    LaunchedEffect(mapLibreMap, uiState.reloadToken) {
        val map = mapLibreMap ?: return@LaunchedEffect
        viewModel.onStyleLoading()
        map.setStyle(uiState.styleUrl) { style ->
            currentStyle = style
            markerController.renderSelectedPlace(style, uiState.searchUiState.selectedPlace)
            routeController.renderRoute(style, uiState.routeUiState)
            viewModel.onStyleLoaded()
            if (uiState.locationPermissionState == LocationPermissionState.Granted) {
                appContainer.locationController.enableLocation(map, style)
                viewModel.setLocationAvailability(
                    appContainer.locationController.locationAvailability(map),
                )
                appContainer.locationController.lastKnownLocation(map)?.let { location ->
                    viewModel.updateCurrentLocation(location.latitude, location.longitude)
                }
            }
        }
    }

    LaunchedEffect(uiState.locationPermissionState, mapLibreMap, currentStyle) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = currentStyle ?: return@LaunchedEffect
        if (uiState.locationPermissionState == LocationPermissionState.Granted) {
            appContainer.locationController.enableLocation(map, style)
            viewModel.setLocationAvailability(
                appContainer.locationController.locationAvailability(map),
            )
            appContainer.locationController.lastKnownLocation(map)?.let { location ->
                viewModel.updateCurrentLocation(location.latitude, location.longitude)
            }
        }
    }

    LaunchedEffect(mapLibreMap, currentStyle, uiState.locationPermissionState) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (currentStyle == null || uiState.locationPermissionState != LocationPermissionState.Granted) {
            viewModel.updateCurrentLocation(null, null)
            return@LaunchedEffect
        }

        while (isActive) {
            viewModel.setLocationAvailability(
                appContainer.locationController.locationAvailability(map),
            )
            val location = appContainer.locationController.lastKnownLocation(map)
            viewModel.updateCurrentLocation(
                latitude = location?.latitude,
                longitude = location?.longitude,
            )
            delay(1_500L)
        }
    }

    LaunchedEffect(currentStyle, uiState.searchUiState.selectedPlace) {
        val style = currentStyle ?: return@LaunchedEffect
        markerController.renderSelectedPlace(style, uiState.searchUiState.selectedPlace)
    }

    LaunchedEffect(currentStyle, uiState.routeUiState) {
        val style = currentStyle ?: return@LaunchedEffect
        routeController.renderRoute(style, uiState.routeUiState)
    }

    LaunchedEffect(uiState.searchUiState.selectedPlace?.selectionId) {
        val selectedPlace = uiState.searchUiState.selectedPlace ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect

        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(selectedPlace.place.latitude, selectedPlace.place.longitude),
                mapPresentation.selectionZoomFor(selectedPlace.place.kind),
            ),
            mapPresentation.selectionDurationMs,
        )
    }

    LaunchedEffect(uiState.routeUiState.fitRequestToken) {
        val fitToken = uiState.routeUiState.fitRequestToken
        if (fitToken <= 0L) return@LaunchedEffect

        val map = mapLibreMap ?: return@LaunchedEffect
        val bounds = routeBounds(uiState.routeUiState) ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, routeFitPaddingPx),
            mapPresentation.routeFitDurationMs,
        )
    }

    LaunchedEffect(uiState.overlayMessage?.id) {
        val message = uiState.overlayMessage ?: return@LaunchedEffect
        val autoDismiss = message.autoDismissMillis ?: return@LaunchedEffect
        delay(autoDismiss)
        viewModel.dismissOverlayMessage(message.id)
    }

    val selectedPlace = uiState.searchUiState.selectedPlace
    val searchPanelVisible = searchFocused || uiState.searchUiState.query.isNotBlank()
    val routePlannerVisible = uiState.routeUiState.plannerVisible
    val hasCurrentLocation = uiState.locationAvailabilityState == LocationAvailabilityState.Available

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        LocateMeButton(
            enabled = uiState.backendState != MapBackendState.Unavailable,
            active = uiState.locationAvailabilityState == LocationAvailabilityState.Available,
            onClick = {
                val map = mapLibreMap ?: return@LocateMeButton
                when (
                    val result = appContainer.locationController.recenterOnUser(
                        map,
                        mapPresentation.recenterZoom,
                        mapPresentation.recenterDurationMs,
                    )
                ) {
                    LocateMeResult.PermissionRequired -> permissionLauncher.launch(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    )

                    else -> viewModel.onLocateMeResult(result)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = 16.dp,
                    bottom = when {
                        routePlannerVisible && selectedPlace != null -> 280.dp
                        routePlannerVisible -> 196.dp
                        selectedPlace != null -> 178.dp
                        else -> 16.dp
                    },
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchBar(
                query = uiState.searchUiState.query,
                onQueryChange = viewModel::updateSearchQuery,
                onClear = { viewModel.updateSearchQuery("") },
                onFocusChanged = { focused -> searchFocused = focused },
                onSearchAction = { focusManager.clearFocus(force = true) },
            )

            if (searchPanelVisible) {
                SearchResultsPanel(
                    searchUiState = uiState.searchUiState,
                    onSelect = { result ->
                        focusManager.clearFocus(force = true)
                        searchFocused = false
                        viewModel.selectSearchResult(result)
                    },
                )
            } else if (uiState.locationPermissionState == LocationPermissionState.Denied) {
                PermissionPrompt(
                    onGrant = {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                )
            }

            uiState.overlayMessage?.let { message ->
                OverlayChip(message = message)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            selectedPlace?.let { place ->
                SelectedPlaceCard(
                    selectedPlace = place,
                    routeUiState = uiState.routeUiState,
                    canRouteFromCurrentLocation = hasCurrentLocation,
                    onSetAsStart = viewModel::useSelectedPlaceAsOrigin,
                    onSetAsDestination = viewModel::useSelectedPlaceAsDestination,
                    onRouteFromCurrentLocation = viewModel::routeFromCurrentLocation,
                    onClear = viewModel::clearSelectedPlace,
                )
            }

            if (routePlannerVisible) {
                RoutePlannerCard(
                    routeUiState = uiState.routeUiState,
                    onTravelModeSelected = viewModel::updateTravelMode,
                    onSwap = viewModel::swapRouteEndpoints,
                    onClear = viewModel::clearRoute,
                    onRequestRoute = viewModel::requestRoute,
                )
            }
        }

        if (uiState.backendState == MapBackendState.Loading && !uiState.hasEverLoadedStyle) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0x52F5F2EC),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (uiState.backendState == MapBackendState.Unavailable && !uiState.hasEverLoadedStyle) {
            FullscreenBackendError(
                backendUrl = uiState.backendUrl,
                message = uiState.backendMessage ?: "Could not reach the self-hosted tile server.",
                onRetry = viewModel::retry,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun routeBounds(routeUiState: RouteUiState): LatLngBounds? {
    val coordinates = mutableListOf<LatLng>()
    routeUiState.path?.coordinates?.forEach { coordinate ->
        coordinates += LatLng(coordinate.latitude, coordinate.longitude)
    }
    routeUiState.origin?.let { coordinates += LatLng(it.place.latitude, it.place.longitude) }
    routeUiState.destination?.let { coordinates += LatLng(it.place.latitude, it.place.longitude) }

    if (coordinates.size < 2) return null

    var north = coordinates.first().latitude
    var south = coordinates.first().latitude
    var east = coordinates.first().longitude
    var west = coordinates.first().longitude

    coordinates.drop(1).forEach { coordinate ->
        north = maxOf(north, coordinate.latitude)
        south = minOf(south, coordinate.latitude)
        east = maxOf(east, coordinate.longitude)
        west = minOf(west, coordinate.longitude)
    }

    return LatLngBounds.from(north, east, south, west)
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSearchAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 2.dp,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState -> onFocusChanged(focusState.isFocused) },
            singleLine = true,
            placeholder = {
                Text("Search Denmark")
            },
            leadingIcon = {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_search),
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_close),
                            contentDescription = "Clear search",
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearchAction()
                },
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun SearchResultsPanel(
    searchUiState: SearchUiState,
    onSelect: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
    ) {
        when (searchUiState.status) {
            SearchStatus.Idle -> {
                Text(
                    text = "Type at least two characters to search for a place, address, or POI.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SearchStatus.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Searching…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SearchStatus.Empty -> {
                Text(
                    text = "No places matched that search.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SearchStatus.Error -> {
                Text(
                    text = searchUiState.errorMessage ?: "Search is temporarily unavailable.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SearchStatus.Results -> {
                Column {
                    searchUiState.results.forEachIndexed { index, result ->
                        SearchResultRow(
                            result = result,
                            onClick = { onSelect(result) },
                        )
                        if (index < searchUiState.results.lastIndex) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            ) {
                                Box(modifier = Modifier.size(width = 1.dp, height = 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = result.place
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = place.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            place.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        place.categoryHint?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SelectedPlaceCard(
    selectedPlace: SelectedPlace,
    routeUiState: RouteUiState,
    canRouteFromCurrentLocation: Boolean,
    onSetAsStart: () -> Unit,
    onSetAsDestination: () -> Unit,
    onRouteFromCurrentLocation: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = selectedPlace.place
    val isCurrentOrigin = routeUiState.origin?.place?.id == place.id
    val isCurrentDestination = routeUiState.destination?.place?.id == place.id

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    place.categoryHint?.let { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = place.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    place.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selectedPlace.origin == SelectedPlaceOrigin.Reverse || place.subtitle == null) {
                        Text(
                            text = place.coordinateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_close),
                        contentDescription = "Clear selected place",
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onSetAsStart,
                        enabled = !isCurrentOrigin,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Set as start")
                    }
                    OutlinedButton(
                        onClick = onSetAsDestination,
                        enabled = !isCurrentDestination,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Set as destination")
                    }
                }
                if (canRouteFromCurrentLocation) {
                    Button(
                        onClick = onRouteFromCurrentLocation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Route from my location")
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutePlannerCard(
    routeUiState: RouteUiState,
    onTravelModeSelected: (TravelMode) -> Unit,
    onSwap: () -> Unit,
    onClear: () -> Unit,
    onRequestRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Route planner",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onSwap,
                        enabled = routeUiState.origin != null && routeUiState.destination != null,
                    ) {
                        Text("Swap")
                    }
                    TextButton(onClick = onClear) {
                        Text("Clear")
                    }
                }
            }

            RouteEndpointRow(
                label = "From",
                title = routeUiState.origin?.title ?: "Choose a start point",
                subtitle = routeUiState.origin?.subtitle,
            )
            RouteEndpointRow(
                label = "To",
                title = routeUiState.destination?.title ?: "Choose a destination",
                subtitle = routeUiState.destination?.subtitle,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TravelMode.values().forEach { mode ->
                    FilterChip(
                        selected = routeUiState.travelMode == mode,
                        onClick = { onTravelModeSelected(mode) },
                        label = {
                            Text(mode.label)
                        },
                    )
                }
            }

            when (routeUiState.status) {
                RouteStatus.Loading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Building ${routeUiState.travelMode.label.lowercase()} route…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                RouteStatus.Ready -> {
                    RouteSummaryBlock(routeUiState = routeUiState)
                }

                RouteStatus.NoRoute,
                RouteStatus.Error,
                -> {
                    Text(
                        text = routeUiState.errorMessage ?: "Route unavailable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (routeUiState.status == RouteStatus.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                else -> Unit
            }

            if (routeUiState.canRequestRoute) {
                Button(
                    onClick = onRequestRoute,
                    enabled = routeUiState.status != RouteStatus.Loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when (routeUiState.status) {
                            RouteStatus.Ready,
                            RouteStatus.NoRoute,
                            RouteStatus.Error,
                            -> "Update route"

                            else -> "Route"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteEndpointRow(
    label: String,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RouteSummaryBlock(
    routeUiState: RouteUiState,
    modifier: Modifier = Modifier,
) {
    val summary = routeUiState.summary ?: return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = routeUiState.travelMode.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = summary.durationLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = summary.distanceLabel,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LocateMeButton(
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(56.dp)
            .shadow(12.dp, CircleShape),
        shape = CircleShape,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_locate),
            contentDescription = "Locate me",
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun PermissionPrompt(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show your live location",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Enable location to see the blue dot and jump back to where you are.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onGrant) {
                Text("Enable")
            }
        }
    }
}

@Composable
private fun OverlayChip(
    message: MapOverlayMessage,
    modifier: Modifier = Modifier,
) {
    val container = when (message.tone) {
        MapOverlayTone.Info -> MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        MapOverlayTone.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
    }
    val content = when (message.tone) {
        MapOverlayTone.Info -> MaterialTheme.colorScheme.onSurface
        MapOverlayTone.Error -> MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        Text(
            text = message.text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = content,
        )
    }
}

@Composable
private fun FullscreenBackendError(
    backendUrl: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Self-hosted map unavailable",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Backend: $backendUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun rememberMapViewWithLifecycle(
    lifecycleOwner: LifecycleOwner,
): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    DisposableEffect(lifecycleOwner, mapView) {
        mapView.onCreate(null)

        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                mapView.onStart()
            }

            override fun onResume(owner: LifecycleOwner) {
                mapView.onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                mapView.onPause()
            }

            override fun onStop(owner: LifecycleOwner) {
                mapView.onStop()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                mapView.onDestroy()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return mapView
}
