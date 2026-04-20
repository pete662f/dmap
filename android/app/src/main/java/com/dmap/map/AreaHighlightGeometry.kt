package com.dmap.map

import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

internal object AreaHighlightGeometry {
    fun fromGeometry(geometry: Geometry): SelectedAreaHighlight? {
        val outlines = when (geometry) {
            is Polygon -> geometry.coordinates().toLineFeatures()
            is MultiPolygon -> geometry.coordinates().flatMap { polygon ->
                polygon.toLineFeatures()
            }
            else -> emptyList()
        }

        if (outlines.isEmpty()) return null

        return SelectedAreaHighlight(
            fillGeometry = FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(geometry))),
            outlineGeometry = FeatureCollection.fromFeatures(outlines),
        )
    }

    private fun List<List<Point>>.toLineFeatures(): List<Feature> {
        return mapNotNull { ring ->
            ring
                .takeIf { it.size >= MIN_RING_POINTS }
                ?.let { Feature.fromGeometry(LineString.fromLngLats(it)) }
        }
    }

    private const val MIN_RING_POINTS = 2
}
