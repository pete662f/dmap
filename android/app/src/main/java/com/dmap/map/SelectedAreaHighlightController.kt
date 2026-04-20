package com.dmap.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
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
        selectedAreaHighlight: SelectedAreaHighlight?,
    ) {
        ensureBound(style)
        val fillSource = style.getSourceAs<GeoJsonSource>(FILL_SOURCE_ID) ?: return
        val outlineSource = style.getSourceAs<GeoJsonSource>(OUTLINE_SOURCE_ID) ?: return
        fillSource.setGeoJson(selectedAreaHighlight?.fillGeometry ?: EMPTY_FEATURE_COLLECTION)
        outlineSource.setGeoJson(selectedAreaHighlight?.outlineGeometry ?: EMPTY_FEATURE_COLLECTION)
    }

    private fun ensureBound(style: Style) {
        if (style.getSource(FILL_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(
                    FILL_SOURCE_ID,
                    EMPTY_FEATURE_COLLECTION,
                ),
            )
        }

        if (style.getSource(OUTLINE_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(
                    OUTLINE_SOURCE_ID,
                    EMPTY_FEATURE_COLLECTION,
                ),
            )
        }

        if (style.getLayer(FILL_LAYER_ID) == null) {
            val layer = FillLayer(FILL_LAYER_ID, FILL_SOURCE_ID).withProperties(
                fillColor(HIGHLIGHT_COLOR),
                fillOpacity(0.24f),
            )
            val anchor = OUTLINE_LAYER_ID.takeIf { style.getLayer(it) != null }
                ?: INSERT_BELOW_LAYER_IDS.firstOrNull { style.getLayer(it) != null }
            if (anchor != null) {
                style.addLayerBelow(layer, anchor)
            } else {
                style.addLayer(layer)
            }
        }

        if (style.getLayer(OUTLINE_LAYER_ID) == null) {
            val layer = LineLayer(OUTLINE_LAYER_ID, OUTLINE_SOURCE_ID).withProperties(
                lineColor(HIGHLIGHT_COLOR),
                lineWidth(3.0f),
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
        private const val FILL_SOURCE_ID = "selected-area-highlight-fill-source"
        private const val OUTLINE_SOURCE_ID = "selected-area-highlight-outline-source"
        private const val FILL_LAYER_ID = "selected-area-highlight-fill-layer"
        private const val OUTLINE_LAYER_ID = "selected-area-highlight-outline-layer"
        private const val HIGHLIGHT_COLOR = "#f59e0b"

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
