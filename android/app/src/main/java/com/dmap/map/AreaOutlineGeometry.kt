package com.dmap.map

import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

internal object AreaOutlineGeometry {
    fun fromGeometry(geometry: Geometry): FeatureCollection? {
        val outlines = when (geometry) {
            is Polygon -> geometry.coordinates().toLineFeatures()
            is MultiPolygon -> geometry.coordinates().flatMap { polygon ->
                polygon.toLineFeatures()
            }
            else -> emptyList()
        }

        return outlines
            .takeIf { it.isNotEmpty() }
            ?.let { FeatureCollection.fromFeatures(it) }
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
