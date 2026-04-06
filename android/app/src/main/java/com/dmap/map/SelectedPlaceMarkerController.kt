package com.dmap.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.appcompat.content.res.AppCompatResources
import com.dmap.R
import com.dmap.place.SelectedPlace
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style

class SelectedPlaceMarkerController(
    private val context: Context,
) {
    private var markerBitmap: Bitmap? = null

    fun renderSelectedPlace(
        style: Style,
        selectedPlace: SelectedPlace?,
    ) {
        ensureBound(style)
        val source = style.getSourceAs<GeoJsonSource>(MARKER_SOURCE_ID) ?: return

        if (selectedPlace == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(arrayOf()))
            return
        }

        source.setGeoJson(
            Feature.fromGeometry(
                Point.fromLngLat(
                    selectedPlace.place.longitude,
                    selectedPlace.place.latitude,
                ),
            ),
        )
    }

    private fun ensureBound(style: Style) {
        if (style.getImage(MARKER_IMAGE_ID) == null) {
            style.addImage(MARKER_IMAGE_ID, markerBitmap())
        }
        if (style.getSource(MARKER_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(
                    MARKER_SOURCE_ID,
                    FeatureCollection.fromFeatures(arrayOf()),
                ),
            )
        }
        if (style.getLayer(MARKER_LAYER_ID) == null) {
            style.addLayer(
                SymbolLayer(MARKER_LAYER_ID, MARKER_SOURCE_ID).withProperties(
                    iconImage(MARKER_IMAGE_ID),
                    iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    iconAllowOverlap(true),
                ),
            )
        }
    }

    private fun markerBitmap(): Bitmap {
        markerBitmap?.let { return it }

        val drawable = AppCompatResources.getDrawable(context, R.drawable.ic_place_pin)
            ?: error("Missing selected-place marker drawable.")
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        markerBitmap = bitmap
        return bitmap
    }

    companion object {
        private const val MARKER_IMAGE_ID = "selected-place-marker-image"
        private const val MARKER_SOURCE_ID = "selected-place-marker-source"
        private const val MARKER_LAYER_ID = "selected-place-marker-layer"
    }
}
