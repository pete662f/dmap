package com.dmap.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.dmap.app.AppContainer
import com.dmap.location.LocationPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
internal fun MapRuntimeEffects(
    appContainer: AppContainer,
    viewModel: MapViewModel,
    uiState: MapUiState,
    mapView: MapView,
    mapPresentation: MapPresentationConfig,
    markerController: SelectedPlaceMarkerController,
    ortofotoLayerController: OrtofotoLayerController,
    areaHighlightController: SelectedAreaHighlightController,
    poiHitDetector: RenderedPoiHitDetector,
    mapLibreMap: MapLibreMap?,
    currentStyle: Style?,
    onMapReady: (MapLibreMap) -> Unit,
    onStyleChanged: (Style) -> Unit,
    onBearingChanged: (Double) -> Unit,
    clearFocus: () -> Unit,
) {
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
            onMapReady(map)
            map.uiSettings.setRotateGesturesEnabled(true)
            map.uiSettings.setTiltGesturesEnabled(false)
            map.uiSettings.setCompassEnabled(false)
            map.setMinZoomPreference(mapPresentation.minZoom)
            map.setMaxZoomPreference(mapPresentation.maxZoom)
            map.setLatLngBoundsForCameraTarget(mapPresentation.cameraBounds)
            map.cameraPosition = mapPresentation.defaultCamera
            onBearingChanged(normalizedBearingDelta(map.cameraPosition.bearing))
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
            onBearingChanged(normalizedBearingDelta(camera.bearing))
            viewModel.updateSearchBias(
                latitude = target.latitude,
                longitude = target.longitude,
                zoom = camera.zoom,
            )
            viewModel.updateCenteredOnUser(map.isCameraCenteredOnUser(target))
        }
        val clickListener = MapLibreMap.OnMapClickListener { point ->
            clearFocus()
            val selectedPoi = poiHitDetector.hitTest(map, point)
            if (selectedPoi != null) {
                viewModel.selectRenderedPoi(selectedPoi)
                true
            } else {
                false
            }
        }
        val longClickListener = MapLibreMap.OnMapLongClickListener { point ->
            clearFocus()
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
        val cameraMoveListener = MapLibreMap.OnCameraMoveListener {
            onBearingChanged(normalizedBearingDelta(map.cameraPosition.bearing))
        }

        map.addOnCameraIdleListener(cameraListener)
        map.addOnCameraMoveListener(cameraMoveListener)
        map.addOnMapClickListener(clickListener)
        map.addOnMapLongClickListener(longClickListener)
        map.addOnMoveListener(moveListener)

        onDispose {
            map.removeOnCameraIdleListener(cameraListener)
            map.removeOnCameraMoveListener(cameraMoveListener)
            map.removeOnMapClickListener(clickListener)
            map.removeOnMapLongClickListener(longClickListener)
            map.removeOnMoveListener(moveListener)
        }
    }

    LaunchedEffect(mapLibreMap, uiState.reloadToken) {
        val map = mapLibreMap ?: return@LaunchedEffect
        viewModel.onStyleLoading()
        map.setStyle(uiState.styleUrl) { style ->
            onStyleChanged(style)
            viewModel.onStyleLoaded()
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

    LaunchedEffect(mapLibreMap, currentStyle, uiState.locationPermissionState) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = currentStyle ?: return@LaunchedEffect
        if (uiState.locationPermissionState != LocationPermissionState.Granted) {
            return@LaunchedEffect
        }

        appContainer.locationController.enableLocation(map, style)
        viewModel.setLocationAvailability(appContainer.locationController.locationAvailability(map))

        while (isActive) {
            delay(1_500L)
            viewModel.setLocationAvailability(appContainer.locationController.locationAvailability(map))
        }
    }

    LaunchedEffect(currentStyle, uiState.searchUiState.selectedPlace) {
        val style = currentStyle ?: return@LaunchedEffect
        markerController.renderSelectedPlace(style, uiState.searchUiState.selectedPlace)
    }

    LaunchedEffect(currentStyle, uiState.searchUiState.selectedAreaHighlight) {
        val style = currentStyle ?: return@LaunchedEffect
        areaHighlightController.renderSelectedArea(style, uiState.searchUiState.selectedAreaHighlight)
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

internal fun normalizedBearingDelta(bearing: Double): Double {
    val normalized = ((bearing % 360.0) + 360.0) % 360.0
    return if (normalized > 180.0) normalized - 360.0 else normalized
}

private fun MapLibreMap.isCameraCenteredOnUser(target: LatLng): Boolean {
    val locationComponent = locationComponent
    val userLocation = if (
        locationComponent.isLocationComponentActivated &&
        locationComponent.isLocationComponentEnabled
    ) {
        locationComponent.lastKnownLocation
    } else {
        null
    }

    return userLocation != null &&
        kotlin.math.abs(target.latitude - userLocation.latitude) < 0.0001 &&
        kotlin.math.abs(target.longitude - userLocation.longitude) < 0.0001
}
