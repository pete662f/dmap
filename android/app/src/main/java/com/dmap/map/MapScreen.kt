package com.dmap.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmap.app.AppContainer
import com.dmap.location.LocationAvailabilityState
import com.dmap.location.LocationPermissionState
import com.dmap.location.LocateMeResult
import kotlin.math.abs
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
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
    var currentBearing by remember { mutableStateOf(0.0) }
    var searchFocused by remember { mutableStateOf(false) }

    val locationPermissions = remember {
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        viewModel.setLocationPermission(granted)
    }

    MapRuntimeEffects(
        appContainer = appContainer,
        viewModel = viewModel,
        uiState = uiState,
        mapView = mapView,
        mapPresentation = mapPresentation,
        markerController = markerController,
        ortofotoLayerController = ortofotoLayerController,
        areaHighlightController = areaHighlightController,
        poiHitDetector = poiHitDetector,
        mapLibreMap = mapLibreMap,
        currentStyle = currentStyle,
        onMapReady = { mapLibreMap = it },
        onStyleChanged = { currentStyle = it },
        onBearingChanged = { currentBearing = it },
        clearFocus = { focusManager.clearFocus(force = true) },
    )

    val selectedPlace = uiState.searchUiState.selectedPlace
    val searchPanelVisible = searchFocused || uiState.searchUiState.query.isNotBlank()
    val locateButtonBottom by animateDpAsState(
        targetValue = if (selectedPlace != null) 116.dp else 16.dp,
        animationSpec = tween(durationMillis = 250),
        label = "locateButtonBottom",
    )

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        MapControls(
            uiState = uiState,
            currentBearing = currentBearing,
            locateButtonBottom = locateButtonBottom,
            onResetBearing = {
                mapLibreMap?.animateCamera(
                    CameraUpdateFactory.bearingTo(0.0),
                    250,
                )
            },
            onToggleLayer = viewModel::toggleBaseLayer,
            onLocateMe = {
                val map = mapLibreMap
                if (map != null) {
                    when (
                        val result = appContainer.locationController.recenterOnUser(
                            map,
                            mapPresentation.recenterZoom,
                            mapPresentation.recenterDurationMs,
                        )
                    ) {
                        LocateMeResult.PermissionRequired -> permissionLauncher.launch(locationPermissions)
                        else -> viewModel.onLocateMeResult(result)
                    }
                }
            },
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
            } else if (uiState.locationPermissionState.isDenied) {
                PermissionPrompt(
                    onGrant = {
                        permissionLauncher.launch(locationPermissions)
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

@Composable
private fun BoxScope.MapControls(
    uiState: MapUiState,
    currentBearing: Double,
    locateButtonBottom: Dp,
    onResetBearing: () -> Unit,
    onToggleLayer: () -> Unit,
    onLocateMe: () -> Unit,
) {
    val mapControlEndPadding = 16.dp
    val mapControlSize = 48.dp
    val mapControlGap = 12.dp
    val layerButtonBottom = locateButtonBottom + mapControlSize + mapControlGap
    val compassBottom = layerButtonBottom + mapControlSize + mapControlGap

    if (abs(currentBearing) > COMPASS_VISIBLE_BEARING_THRESHOLD_DEGREES) {
        NorthArrowButton(
            rotationDegrees = -currentBearing.toFloat(),
            onClick = onResetBearing,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = mapControlEndPadding,
                    bottom = compassBottom,
                ),
        )
    }

    LayerToggleButton(
        baseLayer = uiState.mapBaseLayer,
        onClick = onToggleLayer,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(
                end = mapControlEndPadding,
                bottom = layerButtonBottom,
            ),
    )

    LocateMeButton(
        enabled = uiState.backendState != MapBackendState.Unavailable,
        active = uiState.locationAvailabilityState == LocationAvailabilityState.Available,
        isCenteredOnUser = uiState.isCenteredOnUser,
        onClick = onLocateMe,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(
                end = mapControlEndPadding,
                bottom = locateButtonBottom,
            ),
    )
}

private val LocationPermissionState.isDenied: Boolean
    get() = this == LocationPermissionState.Denied
