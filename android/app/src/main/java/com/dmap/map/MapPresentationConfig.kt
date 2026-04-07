package com.dmap.map

import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import com.dmap.place.PlaceKind

data class MapPresentationConfig(
    val defaultCamera: CameraPosition,
    val cameraBounds: LatLngBounds?,
    val minZoom: Double,
    val maxZoom: Double,
    val recenterZoom: Double,
    val recenterDurationMs: Int,
    val detailSelectionZoom: Double,
    val streetSelectionZoom: Double,
    val areaSelectionZoom: Double,
    val selectionDurationMs: Int,
) {
    fun selectionZoomFor(kind: PlaceKind): Double {
        return when (kind) {
            PlaceKind.City,
            PlaceKind.Town,
            PlaceKind.Village,
            PlaceKind.Postcode,
            PlaceKind.Region,
            PlaceKind.Country,
            -> areaSelectionZoom
            PlaceKind.Street -> streetSelectionZoom
            else -> detailSelectionZoom
        }
    }

    companion object {
        fun denmark(): MapPresentationConfig {
            val defaultCenter = LatLng(56.1725, 10.0938)
            return MapPresentationConfig(
                defaultCamera = CameraPosition.Builder()
                    .target(defaultCenter)
                    .zoom(6.15)
                    .build(),
                cameraBounds = null,
                minZoom = 1.0,
                maxZoom = 18.5,
                recenterZoom = 15.4,
                recenterDurationMs = 1200,
                detailSelectionZoom = 16.2,
                streetSelectionZoom = 15.1,
                areaSelectionZoom = 11.7,
                selectionDurationMs = 950,
            )
        }
    }
}
