package com.dmap.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
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
import com.dmap.place.SelectedPlace
import com.dmap.place.SelectedPlaceType
import com.dmap.place.SearchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun MapScreen(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val viewModel: MapViewModel = viewModel(factory = MapViewModel.factory(appContainer))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapView = rememberMapViewWithLifecycle(lifecycleOwner)
    val mapPresentation = remember { MapPresentationConfig.denmark() }
    val markerController = remember(context) { SelectedPlaceMarkerController(context) }
    val ortofotoLayerController = remember { OrtofotoLayerController() }
    val areaHighlightController = remember { SelectedAreaHighlightController() }
    val poiHitDetector = remember(context) {
        RenderedPoiHitDetector(context.resources.displayMetrics.density)
    }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentStyle by remember { mutableStateOf<Style?>(null) }
    var searchFocused by remember { mutableStateOf(false) }

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
            val locationComponent = map.locationComponent
            val userLocation = if (
                locationComponent.isLocationComponentActivated &&
                locationComponent.isLocationComponentEnabled
            ) {
                locationComponent.lastKnownLocation
            } else {
                null
            }
            val isCentered = userLocation != null &&
                Math.abs(target.latitude - userLocation.latitude) < 0.0001 &&
                Math.abs(target.longitude - userLocation.longitude) < 0.0001
            viewModel.updateCenteredOnUser(isCentered)
        }
        val clickListener = MapLibreMap.OnMapClickListener { point ->
            focusManager.clearFocus(force = true)
            val selectedPoi = poiHitDetector.hitTest(map, point)
            if (selectedPoi != null) {
                viewModel.selectRenderedPoi(selectedPoi)
                true
            } else {
                false
            }
        }
        val longClickListener = MapLibreMap.OnMapLongClickListener { point ->
            focusManager.clearFocus(force = true)
            viewModel.selectCoordinateFromLongPress(
                longitude = point.longitude,
                latitude = point.latitude,
            )
            true
        }

        val moveListener = object : MapLibreMap.OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) {
                viewModel.updateCenteredOnUser(false)
            }
            override fun onMove(detector: MoveGestureDetector) {}
            override fun onMoveEnd(detector: MoveGestureDetector) {}
        }

        map.addOnCameraIdleListener(cameraListener)
        map.addOnMapClickListener(clickListener)
        map.addOnMapLongClickListener(longClickListener)
        map.addOnMoveListener(moveListener)

        onDispose {
            map.removeOnCameraIdleListener(cameraListener)
            map.removeOnMapClickListener(clickListener)
            map.removeOnMapLongClickListener(longClickListener)
            map.removeOnMoveListener(moveListener)
        }
    }

    LaunchedEffect(mapLibreMap, uiState.reloadToken) {
        val map = mapLibreMap ?: return@LaunchedEffect
        viewModel.onStyleLoading()
        map.setStyle(uiState.styleUrl) { style ->
            currentStyle = style
            viewModel.onStyleLoaded()
            if (uiState.locationPermissionState == LocationPermissionState.Granted) {
                appContainer.locationController.enableLocation(map, style)
                viewModel.setLocationAvailability(
                    appContainer.locationController.locationAvailability(map),
                )
            }
        }
    }

    LaunchedEffect(currentStyle, uiState.mapBaseLayer, uiState.imageryTileUrl) {
        val style = currentStyle ?: return@LaunchedEffect
        renderOrtofotoLayer(
            controller = ortofotoLayerController,
            style = style,
            baseLayer = uiState.mapBaseLayer,
            tileUrl = uiState.imageryTileUrl,
            onFailure = viewModel::onImageryLayerFailed,
        )
    }

    LaunchedEffect(uiState.locationPermissionState, mapLibreMap, currentStyle) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = currentStyle ?: return@LaunchedEffect
        if (uiState.locationPermissionState == LocationPermissionState.Granted) {
            appContainer.locationController.enableLocation(map, style)
            viewModel.setLocationAvailability(
                appContainer.locationController.locationAvailability(map),
            )
        }
    }

    LaunchedEffect(mapLibreMap, currentStyle, uiState.locationPermissionState) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (currentStyle == null || uiState.locationPermissionState != LocationPermissionState.Granted) {
            return@LaunchedEffect
        }

        while (isActive) {
            viewModel.setLocationAvailability(
                appContainer.locationController.locationAvailability(map),
            )
            delay(1_500L)
        }
    }

    LaunchedEffect(currentStyle, uiState.searchUiState.selectedPlace) {
        val style = currentStyle ?: return@LaunchedEffect
        markerController.renderSelectedPlace(style, uiState.searchUiState.selectedPlace)
    }

    LaunchedEffect(currentStyle, uiState.searchUiState.selectedAreaOutline) {
        val style = currentStyle ?: return@LaunchedEffect
        areaHighlightController.renderSelectedArea(style, uiState.searchUiState.selectedAreaOutline)
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

    LaunchedEffect(uiState.overlayMessage?.id) {
        val message = uiState.overlayMessage ?: return@LaunchedEffect
        val autoDismiss = message.autoDismissMillis ?: return@LaunchedEffect
        delay(autoDismiss)
        viewModel.dismissOverlayMessage(message.id)
    }

    val searchPanelVisible = searchFocused || uiState.searchUiState.query.isNotBlank()
    val selectedPlace = uiState.searchUiState.selectedPlace

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        val locateButtonBottom by animateDpAsState(
            targetValue = if (selectedPlace != null) 116.dp else 16.dp,
            animationSpec = tween(durationMillis = 250),
            label = "locateButtonBottom",
        )

        LayerToggleButton(
            baseLayer = uiState.mapBaseLayer,
            onClick = viewModel::toggleBaseLayer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = 16.dp,
                    bottom = locateButtonBottom + 60.dp,
                ),
        )

        LocateMeButton(
            enabled = uiState.backendState != MapBackendState.Unavailable,
            active = uiState.locationAvailabilityState == LocationAvailabilityState.Available,
            isCenteredOnUser = uiState.isCenteredOnUser,
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
                    bottom = locateButtonBottom,
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

        selectedPlace?.let { place ->
            SelectedPlaceCard(
                selectedPlace = place,
                isEnrichingPlace = uiState.searchUiState.isEnrichingPlace,
                onClear = viewModel::clearSelectedPlace,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
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

private fun renderOrtofotoLayer(
    controller: OrtofotoLayerController,
    style: Style,
    baseLayer: MapBaseLayer,
    tileUrl: String?,
    onFailure: (String?) -> Unit,
) {
    runCatching {
        controller.render(style, baseLayer, tileUrl)
    }.onFailure { error ->
        controller.remove(style)
        onFailure(error.message)
    }
}

@Composable
private fun LayerToggleButton(
    baseLayer: MapBaseLayer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ortofotoActive = baseLayer == MapBaseLayer.Ortofoto
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_layers),
                contentDescription = if (ortofotoActive) "Show map" else "Show Ortofoto",
                tint = if (ortofotoActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
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
        PlaceSummaryText(
            place = place,
            modifier = Modifier.weight(1f),
            titleStyle = MaterialTheme.typography.titleSmall,
            showCoordinates = false,
        )
    }
}

@Composable
private fun SelectedPlaceCard(
    selectedPlace: SelectedPlace,
    isEnrichingPlace: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = selectedPlace.place
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PlaceSummaryText(
                place = place,
                modifier = Modifier.weight(1f),
                titleStyle = MaterialTheme.typography.titleMedium,
                showCoordinates = selectedPlace.type == SelectedPlaceType.CoordinatePin &&
                    place.title == "Dropped pin",
                isLoading = isEnrichingPlace,
            )
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_close),
                    contentDescription = "Clear selected place",
                )
            }
        }
    }
}

@Composable
private fun PlaceSummaryText(
    place: com.dmap.place.PlaceSummary,
    titleStyle: TextStyle,
    showCoordinates: Boolean,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
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
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
        )
        if (isLoading && place.subtitle == null) {
            TextLinePlaceholder()
        } else if (place.subtitle != null) {
            Text(
                text = place.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showCoordinates) {
            Text(
                text = place.coordinateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextLinePlaceholder() {
    val lineHeightDp = with(LocalDensity.current) {
        MaterialTheme.typography.bodySmall.lineHeight.toDp()
    }
    val infiniteTransition = rememberInfiniteTransition(label = "placeholder")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "placeholderAlpha",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .height(lineHeightDp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                shape = RoundedCornerShape(4.dp),
            ),
    )
}

@Composable
private fun LocateMeButton(
    enabled: Boolean,
    active: Boolean,
    isCenteredOnUser: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_locate),
                contentDescription = "Locate me",
                tint = when {
                    isCenteredOnUser -> Color(0xFF9E9E9E)
                    active -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(24.dp),
            )
        }
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
