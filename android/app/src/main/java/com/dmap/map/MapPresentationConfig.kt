package com.dmap.map

import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

data class MapPresentationConfig(
    val defaultCamera: CameraPosition,
    val cameraBounds: LatLngBounds,
    val minZoom: Double,
    val maxZoom: Double,
    val recenterZoom: Double,
    val recenterDurationMs: Int,
) {
    companion object {
        fun denmark(): MapPresentationConfig {
            val defaultCenter = LatLng(56.1725, 10.0938)
            return MapPresentationConfig(
                defaultCamera = CameraPosition.Builder()
                    .target(defaultCenter)
                    .zoom(6.15)
                    .build(),
                cameraBounds = LatLngBounds.from(
                    58.15,
                    15.9,
                    54.25,
                    7.5,
                ),
                minZoom = 5.1,
                maxZoom = 18.5,
                recenterZoom = 15.4,
                recenterDurationMs = 1200,
            )
        }
    }
}
