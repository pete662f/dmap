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
    ): RenderedPoiSelection? {
        val tapPointF = map.projection.toScreenLocation(tapLatLng)
        val tapPoint = tapPointF.toScreenPoint()
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
                return RenderedPoiSelection(
                    place = selected,
                    areaOutline = chooseMatchingAreaCandidate(
                        title = selected.title,
                        candidates = queryAreaCandidates(
                            queryFeatures = { layerId ->
                                map.queryRenderedFeatures(tapPointF, layerId)
                            },
                            tapLatLng = tapLatLng,
                        ),
                    )?.areaOutline,
                )
            }
        }

        return chooseBestAreaCandidate(
            queryAreaCandidates(
                queryFeatures = { layerId ->
                    map.queryRenderedFeatures(tapPointF, layerId)
                },
                tapLatLng = tapLatLng,
            ),
        )?.toSelection()
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
        return parsePointFeature(feature)
    }

    internal fun parsePointFeature(feature: Feature): PlaceSummary? {
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

    internal fun parseAreaFeature(
        feature: Feature,
        layerId: String,
        tapLatLng: LatLng,
    ): AreaCandidate? {
        if (layerId !in AREA_POI_LAYER_IDS) return null

        val geometry = feature.geometry() ?: return null
        val areaOutline = AreaOutlineGeometry.fromGeometry(geometry) ?: return null
        val name = firstNonBlank(
            feature.stringProperty("name"),
            feature.stringProperty("name_da"),
            feature.stringProperty("name_en"),
        )
        val featureClass = feature.stringProperty("class")
        val fallbackCategory = fallbackCategory(layerId)
        val category = formatLabel(featureClass) ?: fallbackCategory
        val title = firstNonBlank(
            name,
            category,
            fallbackCategory,
        ) ?: fallbackCategory

        return AreaCandidate(
            layerId = layerId,
            hasName = name != null,
            place = PlaceSummary(
                id = buildAreaFeatureId(feature, layerId, title, tapLatLng),
                title = title,
                subtitle = null,
                latitude = tapLatLng.latitude,
                longitude = tapLatLng.longitude,
                kind = PlaceKind.Poi,
                categoryHint = category,
            ),
            areaOutline = areaOutline,
        )
    }

    internal fun queryAreaCandidates(
        queryFeatures: (String) -> List<Feature>,
        tapLatLng: LatLng,
    ): List<AreaCandidate> {
        return AREA_POI_LAYER_IDS.flatMap { layerId ->
            queryFeatures(layerId).mapNotNull { feature ->
                parseAreaFeature(
                    feature = feature,
                    layerId = layerId,
                    tapLatLng = tapLatLng,
                )
            }
        }
    }

    internal fun chooseBestAreaCandidate(candidates: List<AreaCandidate>): AreaCandidate? {
        return candidates.minWithOrNull(AREA_CANDIDATE_COMPARATOR)
    }

    internal fun chooseMatchingAreaCandidate(
        title: String,
        candidates: List<AreaCandidate>,
    ): AreaCandidate? {
        val normalizedTitle = normalizeTitle(title)
        if (normalizedTitle == null) return null

        return chooseBestAreaCandidate(
            candidates.filter { candidate ->
                normalizeTitle(candidate.place.title) == normalizedTitle
            },
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

    private fun buildAreaFeatureId(
        feature: Feature,
        layerId: String,
        title: String,
        tapLatLng: LatLng,
    ): String {
        val osmType = feature.stringProperty("osm_type")
        val osmId = feature.stringProperty("osm_id")
        if (osmType != null && osmId != null) {
            return "$osmType:$osmId"
        }

        return listOf(
            "area",
            layerId,
            title,
            tapLatLng.latitude.toString(),
            tapLatLng.longitude.toString(),
        ).joinToString(":")
    }

    private fun fallbackCategory(layerId: String): String {
        return when (layerId) {
            "landuse_school" -> "School"
            "landuse_hospital" -> "Hospital"
            "landuse_cemetery" -> "Cemetery"
            "landuse_pitch" -> "Pitch"
            "landuse_track" -> "Track"
            else -> "Park"
        }
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

    private fun normalizeTitle(raw: String?): String? {
        return raw
            ?.trim()
            ?.lowercase(LOCALE)
            ?.replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.ifBlank { null }
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

    internal data class AreaCandidate(
        val layerId: String,
        val hasName: Boolean,
        val place: PlaceSummary,
        val areaOutline: org.maplibre.geojson.FeatureCollection,
    ) {
        fun toSelection(): RenderedPoiSelection {
            return RenderedPoiSelection(
                place = place,
                areaOutline = areaOutline,
            )
        }
    }

    companion object {
        internal const val HIT_TOLERANCE_DP = 20f
        internal val POI_LAYER_IDS = listOf(
            "poi_transit",
            "poi_z14",
            "poi_z15",
            "poi_z16",
        )
        internal val AREA_POI_LAYER_IDS = listOf(
            "park",
            "landuse_school",
            "landuse_hospital",
            "landuse_cemetery",
            "landuse_pitch",
            "landuse_track",
        )

        private val LOCALE = Locale("da", "DK")
        private val AREA_LAYER_SPECIFICITY = mapOf(
            "landuse_school" to 0,
            "landuse_hospital" to 1,
            "landuse_cemetery" to 2,
            "landuse_pitch" to 3,
            "landuse_track" to 4,
            "park" to 5,
        )
        private val AREA_CANDIDATE_COMPARATOR = compareBy<AreaCandidate> {
            if (it.hasName) 0 else 1
        }.thenBy {
            if (it.hasName && it.layerId == "park") 0 else if (it.hasName) 1 else 0
        }.thenBy {
            AREA_LAYER_SPECIFICITY[it.layerId] ?: Int.MAX_VALUE
        }.thenBy {
            it.place.id
        }
    }
}

private fun android.graphics.PointF.toScreenPoint(): RenderedPoiHitDetector.ScreenPoint {
    return RenderedPoiHitDetector.ScreenPoint(x = x, y = y)
}
