package com.dmap.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

class OrtofotoLayerController {
    fun render(
        style: Style,
        baseLayer: MapBaseLayer,
        tileUrl: String?,
    ) {
        if (baseLayer != MapBaseLayer.Ortofoto || tileUrl.isNullOrBlank()) {
            remove(style)
            return
        }

        if (style.getSource(SOURCE_ID) == null) {
            style.addSource(
                RasterSource(
                    SOURCE_ID,
                    TileSet("2.1.0", tileUrl).apply {
                        attribution = ATTRIBUTION
                        @Suppress("DEPRECATION")
                        setBounds(arrayOf(WEST, SOUTH, EAST, NORTH))
                        setMaxZoom(MAX_ZOOM)
                    },
                    TILE_SIZE,
                ),
            )
        }

        if (style.getLayer(LAYER_ID) == null) {
            val layer = RasterLayer(LAYER_ID, SOURCE_ID)
            val anchor = INSERT_BELOW_LAYER_IDS.firstOrNull { style.getLayer(it) != null }
            if (anchor != null) {
                style.addLayerBelow(layer, anchor)
            } else {
                style.addLayerAt(layer, FALLBACK_LAYER_INDEX)
            }
        }
    }

    fun remove(style: Style) {
        if (style.getLayer(LAYER_ID) != null) {
            style.removeLayer(LAYER_ID)
        }
        if (style.getSource(SOURCE_ID) != null) {
            style.removeSource(SOURCE_ID)
        }
    }

    companion object {
        private const val SOURCE_ID = "dmap-ortofoto-source"
        private const val LAYER_ID = "dmap-ortofoto-layer"
        private const val TILE_SIZE = 256
        private const val MAX_ZOOM = 20f
        private const val FALLBACK_LAYER_INDEX = 1

        private const val WEST = 2.478420f
        private const val SOUTH = 53.015000f
        private const val EAST = 17.557800f
        private const val NORTH = 58.640300f
        private const val ATTRIBUTION = "Klimadatastyrelsen / GeoDanmark Ortofoto, CC BY 4.0"

        private val INSERT_BELOW_LAYER_IDS = listOf(
            "water_name_line",
            "water_name_point",
            "poi_z16",
            "poi_z15",
            "poi_z14",
            "poi_transit",
            "road_label",
            "place_village",
            "place_town",
            "place_city",
        )
    }
}
