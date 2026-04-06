package com.dmap.map

import android.graphics.RectF
import com.dmap.place.PlaceKind
import com.dmap.place.PlaceSummary
import java.util.Locale
import kotlin.math.pow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

class RenderedPoiHitDetector(
    density: Float,
) {
    private val hitTolerancePx = HIT_TOLERANCE_DP * density

    fun hitTest(
        map: MapLibreMap,
        tapLatLng: LatLng,
    ): PlaceSummary? {
        val tapPoint = map.projection.toScreenLocation(tapLatLng).toScreenPoint()
        val hitRect = RectF(
            tapPoint.x - hitTolerancePx,
            tapPoint.y - hitTolerancePx,
            tapPoint.x + hitTolerancePx,
            tapPoint.y + hitTolerancePx,
        )

        for (layerId in POI_LAYER_IDS) {
            val features = map.queryRenderedFeatures(hitRect, layerId)
            val selected = chooseBestCandidate(
                tapPoint = tapPoint,
                features = features,
                toScreenPoint = { geometryPoint ->
                    map.projection
                        .toScreenLocation(LatLng(geometryPoint.latitude(), geometryPoint.longitude()))
                        .toScreenPoint()
                },
            )
            if (selected != null) {
                return selected
            }
        }

        return null
    }

    internal fun chooseBestCandidate(
        tapPoint: ScreenPoint,
        features: List<Feature>,
        toScreenPoint: (Point) -> ScreenPoint,
    ): PlaceSummary? {
        return features
            .mapNotNull { feature ->
                val geometryPoint = feature.geometry() as? Point ?: return@mapNotNull null
                val place = parseFeature(feature) ?: return@mapNotNull null
                PoiCandidate(
                    place = place,
                    distanceSquared = tapPoint.distanceSquaredTo(toScreenPoint(geometryPoint)),
                )
            }
            .minWithOrNull(
                compareBy<PoiCandidate> { it.distanceSquared }
                    .thenBy { it.place.id },
            )
            ?.place
    }

    internal fun parseFeature(feature: Feature): PlaceSummary? {
        val geometryPoint = feature.geometry() as? Point ?: return null
        val name = feature.stringProperty("name")
        val englishName = feature.stringProperty("name_en")
        val featureClass = feature.stringProperty("class")
        val subclass = feature.stringProperty("subclass")

        return PlaceSummary(
            id = buildFeatureId(feature, geometryPoint, featureClass, subclass, name),
            title = firstNonBlank(
                name,
                englishName,
                formatLabel(subclass),
                formatLabel(featureClass),
                "Selected place",
            ) ?: "Selected place",
            subtitle = null,
            latitude = geometryPoint.latitude(),
            longitude = geometryPoint.longitude(),
            kind = PlaceKind.Poi,
            categoryHint = formatLabel(subclass ?: featureClass),
        )
    }

    private fun buildFeatureId(
        feature: Feature,
        geometryPoint: Point,
        featureClass: String?,
        subclass: String?,
        name: String?,
    ): String {
        val osmType = feature.stringProperty("osm_type")
        val osmId = feature.stringProperty("osm_id")
        if (osmType != null && osmId != null) {
            return "$osmType:$osmId"
        }

        return listOf(
            "poi",
            featureClass ?: "unknown",
            subclass ?: "unknown",
            name ?: "unknown",
            geometryPoint.latitude().toString(),
            geometryPoint.longitude().toString(),
        ).joinToString(":")
    }

    private fun formatLabel(raw: String?): String? {
        val normalized = raw
            ?.trim()
            ?.replace('_', ' ')
            ?.replace('-', ' ')
            ?.lowercase(LOCALE)
            ?.ifBlank { return null }
            ?: return null

        return normalized.split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { character ->
                    if (character.isLowerCase()) {
                        character.titlecase(LOCALE)
                    } else {
                        character.toString()
                    }
                }
            }
            .ifBlank { null }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }

    private fun Feature.stringProperty(key: String): String? {
        return runCatching {
            if (hasNonNullValueForProperty(key)) {
                getStringProperty(key)?.trim()?.ifBlank { null }
            } else {
                null
            }
        }.getOrNull()
    }

    internal data class ScreenPoint(
        val x: Float,
        val y: Float,
    ) {
        fun distanceSquaredTo(other: ScreenPoint): Double {
            return (x - other.x).toDouble().pow(2) + (y - other.y).toDouble().pow(2)
        }
    }

    private data class PoiCandidate(
        val place: PlaceSummary,
        val distanceSquared: Double,
    )

    companion object {
        internal const val HIT_TOLERANCE_DP = 20f
        internal val POI_LAYER_IDS = listOf(
            "poi_transit",
            "poi_z14",
            "poi_z15",
            "poi_z16",
        )

        private val LOCALE = Locale("da", "DK")
    }
}

private fun android.graphics.PointF.toScreenPoint(): RenderedPoiHitDetector.ScreenPoint {
    return RenderedPoiHitDetector.ScreenPoint(x = x, y = y)
}
