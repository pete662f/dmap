package com.dmap.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection

class SelectedAreaHighlightController {
    fun renderSelectedArea(
        style: Style,
        selectedAreaOutline: FeatureCollection?,
    ) {
        ensureBound(style)
        val source = style.getSourceAs<GeoJsonSource>(HIGHLIGHT_SOURCE_ID) ?: return
        source.setGeoJson(selectedAreaOutline ?: EMPTY_FEATURE_COLLECTION)
    }

    private fun ensureBound(style: Style) {
        if (style.getSource(HIGHLIGHT_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(
                    HIGHLIGHT_SOURCE_ID,
                    EMPTY_FEATURE_COLLECTION,
                ),
            )
        }

        if (style.getLayer(HIGHLIGHT_LAYER_ID) == null) {
            val layer = LineLayer(HIGHLIGHT_LAYER_ID, HIGHLIGHT_SOURCE_ID).withProperties(
                lineColor("#1d4ed8"),
                lineWidth(2.5f),
                lineOpacity(0.95f),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineCap(Property.LINE_CAP_ROUND),
            )
            val anchor = INSERT_BELOW_LAYER_IDS.firstOrNull { style.getLayer(it) != null }
            if (anchor != null) {
                style.addLayerBelow(layer, anchor)
            } else {
                style.addLayer(layer)
            }
        }
    }

    companion object {
        private const val HIGHLIGHT_SOURCE_ID = "selected-area-highlight-source"
        private const val HIGHLIGHT_LAYER_ID = "selected-area-highlight-layer"

        private val EMPTY_FEATURE_COLLECTION = FeatureCollection.fromFeatures(arrayOf())
        private val INSERT_BELOW_LAYER_IDS = listOf(
            "poi_transit",
            "poi_z14",
            "poi_z15",
            "poi_z16",
            "road_label",
            "place_village",
            "place_town",
            "place_city",
        )
    }
}
