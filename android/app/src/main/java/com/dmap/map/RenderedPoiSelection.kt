package com.dmap.map

import com.dmap.place.PlaceSummary
import org.maplibre.geojson.FeatureCollection

data class SelectedAreaHighlight(
    val fillGeometry: FeatureCollection,
    val outlineGeometry: FeatureCollection,
)

data class RenderedPoiSelection(
    val place: PlaceSummary,
    val areaHighlight: SelectedAreaHighlight? = null,
)
