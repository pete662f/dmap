package com.dmap.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun MapScreen(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: MapViewModel = viewModel(factory = MapViewModel.factory(appContainer))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapView = rememberMapViewWithLifecycle(lifecycleOwner)
    val mapPresentation = remember { MapPresentationConfig.denmark() }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentStyle by remember { mutableStateOf<Style?>(null) }

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
            mapLibreMap = map
            map.uiSettings.setRotateGesturesEnabled(true)
            map.uiSettings.setTiltGesturesEnabled(false)
            map.uiSettings.setCompassEnabled(true)
            map.setMinZoomPreference(mapPresentation.minZoom)
            map.setMaxZoomPreference(mapPresentation.maxZoom)
            map.setLatLngBoundsForCameraTarget(mapPresentation.cameraBounds)
            map.cameraPosition = mapPresentation.defaultCamera
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

    LaunchedEffect(uiState.overlayMessage?.id) {
        val message = uiState.overlayMessage ?: return@LaunchedEffect
        val autoDismiss = message.autoDismissMillis ?: return@LaunchedEffect
        delay(autoDismiss)
        viewModel.dismissOverlayMessage(message.id)
    }

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
                .padding(end = 16.dp, bottom = 16.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (uiState.locationPermissionState == LocationPermissionState.Denied) {
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
    val context = androidx.compose.ui.platform.LocalContext.current
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
