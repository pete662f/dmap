package com.dmap.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.dmap.R
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

class LocationController(
    private val context: Context,
) {
    @ColorInt
    private val dotBlue = ContextCompat.getColor(context, R.color.location_dot_blue)

    @ColorInt
    private val dotBlueDark = ContextCompat.getColor(context, R.color.location_dot_blue_dark)

    @ColorInt
    private val dotWhite = ContextCompat.getColor(context, R.color.location_dot_white)

    @ColorInt
    private val dotAccuracy = ContextCompat.getColor(context, R.color.location_accuracy_blue)

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun enableLocation(mapLibreMap: MapLibreMap, style: Style) {
        if (!hasLocationPermission()) return

        val locationComponent = mapLibreMap.locationComponent
        val options = LocationComponentOptions.createFromAttributes(
            context,
            org.maplibre.android.R.style.maplibre_LocationComponent,
        ).toBuilder()
            .accuracyColor(dotAccuracy)
            .accuracyAlpha(0.18f)
            .foregroundTintColor(dotBlue)
            .backgroundTintColor(dotWhite)
            .bearingTintColor(dotBlueDark)
            .trackingGesturesManagement(false)
            .trackingAnimationDurationMultiplier(0.85f)
            .pulseEnabled(false)
            .build()

        if (!locationComponent.isLocationComponentActivated) {
            locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .locationComponentOptions(options)
                    .useDefaultLocationEngine(true)
                    .build(),
            )
        } else {
            locationComponent.applyStyle(options)
        }

        locationComponent.setLocationComponentEnabled(true)
        locationComponent.setCameraMode(CameraMode.NONE)
        locationComponent.setRenderMode(RenderMode.NORMAL)
    }

    fun locationAvailability(mapLibreMap: MapLibreMap?): LocationAvailabilityState {
        if (!hasLocationPermission() || mapLibreMap == null) return LocationAvailabilityState.Idle

        val locationComponent = mapLibreMap.locationComponent
        if (!locationComponent.isLocationComponentActivated || !locationComponent.isLocationComponentEnabled) {
            return LocationAvailabilityState.Unavailable
        }

        return if (locationComponent.lastKnownLocation != null) {
            LocationAvailabilityState.Available
        } else {
            LocationAvailabilityState.Locating
        }
    }

    fun recenterOnUser(
        mapLibreMap: MapLibreMap,
        zoom: Double,
        durationMs: Int,
    ): LocateMeResult {
        if (!hasLocationPermission()) return LocateMeResult.PermissionRequired

        val locationComponent = mapLibreMap.locationComponent
        if (!locationComponent.isLocationComponentActivated || !locationComponent.isLocationComponentEnabled) {
            return LocateMeResult.Unavailable
        }

        val lastKnownLocation = locationComponent.lastKnownLocation ?: return LocateMeResult.WaitingForFix
        mapLibreMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude),
                zoom,
            ),
            durationMs,
        )
        return LocateMeResult.Centered
    }
}
