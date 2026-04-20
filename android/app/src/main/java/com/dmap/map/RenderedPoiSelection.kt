package com.dmap.map

import com.dmap.place.PlaceSummary
import org.maplibre.geojson.FeatureCollection

data class RenderedPoiSelection(
    val place: PlaceSummary,
    val areaOutline: FeatureCollection? = null,
)
