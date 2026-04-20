package com.dmap.map

import android.graphics.RectF
import com.dmap.place.PlaceKind
import com.dmap.place.PlaceSummary
import java.util.Locale
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

class RenderedPoiHitDetector(
    private val density: Float,
) {
    private val hitTolerancePx = HIT_TOLERANCE_DP * density
    private val poiAreaFallbackTolerancePx = POI_AREA_FALLBACK_TOLERANCE_DP * density

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

        return selectionFromRenderedFeatures(
            tapPoint = tapPoint,
            pointFeaturesByLayer = { layerId ->
                map.queryRenderedFeatures(hitRect, layerId)
            },
            areaFeaturesByLayer = { layerId, geometryPoint ->
                val poiPoint = map.projection
                    .toScreenLocation(LatLng(geometryPoint.latitude(), geometryPoint.longitude()))
                if (layerId == POI_AREA_HITBOX_LAYER_ID) {
                    val pointHits = map.queryRenderedFeatures(poiPoint, layerId)
                    if (pointHits.isNotEmpty()) {
                        pointHits
                    } else {
                        map.queryRenderedFeatures(
                            RectF(
                                poiPoint.x - poiAreaFallbackTolerancePx,
                                poiPoint.y - poiAreaFallbackTolerancePx,
                                poiPoint.x + poiAreaFallbackTolerancePx,
                                poiPoint.y + poiAreaFallbackTolerancePx,
                            ),
                            layerId,
                        )
                    }
                } else {
                    val poiHitRect = RectF(
                        poiPoint.x - hitTolerancePx,
                        poiPoint.y - hitTolerancePx,
                        poiPoint.x + hitTolerancePx,
                        poiPoint.y + hitTolerancePx,
                    )
                    map.queryRenderedFeatures(poiHitRect, layerId)
                }
            },
            tapLatLng = tapLatLng,
            toScreenPoint = { geometryPoint ->
                map.projection
                    .toScreenLocation(LatLng(geometryPoint.latitude(), geometryPoint.longitude()))
                    .toScreenPoint()
            },
        )
    }

    internal fun selectionFromRenderedFeatures(
        tapPoint: ScreenPoint,
        pointFeaturesByLayer: (String) -> List<Feature>,
        areaFeaturesByLayer: (String, Point) -> List<Feature>,
        tapLatLng: LatLng,
        toScreenPoint: (Point) -> ScreenPoint,
    ): RenderedPoiSelection? {
        for (layerId in POI_LAYER_IDS) {
            val features = pointFeaturesByLayer(layerId)
            val selected = chooseBestPoiCandidate(
                tapPoint = tapPoint,
                features = features,
                toScreenPoint = toScreenPoint,
            )
            if (selected != null) {
                return RenderedPoiSelection(
                    place = selected.place,
                    areaOutline = chooseAreaOutlineForPointPoi(
                        selected = selected,
                        areaFeaturesByLayer = areaFeaturesByLayer,
                        tapLatLng = tapLatLng,
                    ),
                )
            }
        }

        return null
    }

    internal fun chooseBestCandidate(
        tapPoint: ScreenPoint,
        features: List<Feature>,
        toScreenPoint: (Point) -> ScreenPoint,
    ): PlaceSummary? {
        return chooseBestPoiCandidate(
            tapPoint = tapPoint,
            features = features,
            toScreenPoint = toScreenPoint,
        )?.place
    }

    private fun chooseBestPoiCandidate(
        tapPoint: ScreenPoint,
        features: List<Feature>,
        toScreenPoint: (Point) -> ScreenPoint,
    ): PoiCandidate? {
        return features
            .mapNotNull { feature ->
                val geometryPoint = feature.geometry() as? Point ?: return@mapNotNull null
                val parsed = parsePointFeatureForSelection(feature) ?: return@mapNotNull null
                PoiCandidate(
                    place = parsed.place,
                    geometryPoint = geometryPoint,
                    poiClass = parsed.poiClass,
                    poiSubclass = parsed.poiSubclass,
                    distanceSquared = tapPoint.distanceSquaredTo(toScreenPoint(geometryPoint)),
                )
            }
            .minWithOrNull(
                compareBy<PoiCandidate> { it.distanceSquared }
                    .thenBy { it.place.id },
            )
    }

    internal fun parsePointFeature(feature: Feature): PlaceSummary? {
        return parsePointFeatureForSelection(feature)?.place
    }

    private fun parsePointFeatureForSelection(feature: Feature): ParsedPointFeature? {
        val geometryPoint = feature.geometry() as? Point ?: return null
        val name = feature.stringProperty("name")
        val danishName = firstNonBlank(
            feature.stringProperty("name:da"),
            feature.stringProperty("name_da"),
        )
        val englishName = firstNonBlank(
            feature.stringProperty("name:en"),
            feature.stringProperty("name_en"),
        )
        val featureClass = feature.stringProperty("class")
        val subclass = feature.stringProperty("subclass")

        return ParsedPointFeature(
            place = PlaceSummary(
                id = buildFeatureId(feature, geometryPoint, featureClass, subclass, name),
                title = firstNonBlank(
                    name,
                    danishName,
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
            ),
            poiClass = featureClass,
            poiSubclass = subclass,
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
            feature.stringProperty("name:da"),
            feature.stringProperty("name_da"),
            feature.stringProperty("name:en"),
            feature.stringProperty("name_en"),
        )
        val featureClass = feature.stringProperty("class")
        val subclass = feature.stringProperty("subclass")
        val areaMeters = feature.doubleProperty("area_m2")
        val category: String
        val title: String
        if (layerId == POI_AREA_HITBOX_LAYER_ID) {
            category = firstNonBlank(
                formatLabel(subclass),
                formatLabel(featureClass),
                "Area",
            ) ?: "Area"
            title = firstNonBlank(
                name,
                formatLabel(subclass),
                formatLabel(featureClass),
                "Selected area",
            ) ?: "Selected area"
        } else {
            val fallbackCategory = fallbackCategory(layerId)
            category = formatLabel(featureClass) ?: fallbackCategory
            title = firstNonBlank(
                name,
                category,
                fallbackCategory,
            ) ?: fallbackCategory
        }

        return AreaCandidate(
            layerId = layerId,
            hasName = name != null,
            featureClass = featureClass,
            subclass = subclass,
            areaMeters = areaMeters,
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

    private fun queryAreaCandidates(
        layerIds: List<String>,
        queryFeatures: (String) -> List<Feature>,
        tapLatLng: LatLng,
    ): List<AreaCandidate> {
        return layerIds.flatMap { layerId ->
            queryFeatures(layerId).mapNotNull { feature ->
                parseAreaFeature(
                    feature = feature,
                    layerId = layerId,
                    tapLatLng = tapLatLng,
                )
            }
        }
    }

    internal fun chooseAreaForPointPoi(
        pointTitle: String,
        pointClass: String?,
        pointSubclass: String?,
        candidates: List<AreaCandidate>,
    ): AreaCandidate? {
        val normalizedTitle = normalizeTitle(pointTitle)
        if (normalizedTitle != null && normalizedTitle !in GENERIC_AREA_TITLES) {
            val sameTitle = chooseBestAreaCandidate(
                candidates.filter { candidate ->
                    candidate.hasName && normalizeTitle(candidate.place.title) == normalizedTitle
                },
            )
            if (sameTitle != null) return sameTitle
        }

        val normalizedPointClass = normalizeRawClass(pointClass)
        val normalizedPointSubclass = normalizeRawClass(pointSubclass)
        if (normalizedPointClass == null && normalizedPointSubclass == null) return null

        return chooseBestAreaCandidate(
            candidates.filter { candidate ->
                val candidateClass = normalizeRawClass(candidate.featureClass)
                val candidateSubclass = normalizeRawClass(candidate.subclass)
                (
                    normalizedPointSubclass != null &&
                        normalizedPointSubclass == candidateSubclass
                    ) || (
                    normalizedPointClass != null &&
                        normalizedPointClass == candidateClass
                    )
            },
        )
    }

    private fun chooseAreaOutlineForPointPoi(
        selected: PoiCandidate,
        areaFeaturesByLayer: (String, Point) -> List<Feature>,
        tapLatLng: LatLng,
    ): org.maplibre.geojson.FeatureCollection? {
        val hitboxCandidates = queryAreaCandidates(
            layerIds = POI_AREA_HITBOX_LAYER_IDS,
            queryFeatures = { layerId ->
                areaFeaturesByLayer(layerId, selected.geometryPoint)
            },
            tapLatLng = tapLatLng,
        )
        val hitboxMatch = chooseAreaForPointPoi(
            pointTitle = selected.place.title,
            pointClass = selected.poiClass,
            pointSubclass = selected.poiSubclass,
            candidates = hitboxCandidates,
        )
        if (hitboxMatch != null) return hitboxMatch.areaOutline

        val fallbackCandidates = queryAreaCandidates(
            layerIds = LEGACY_AREA_POI_LAYER_IDS,
            queryFeatures = { layerId ->
                areaFeaturesByLayer(layerId, selected.geometryPoint)
            },
            tapLatLng = tapLatLng,
        )
        return chooseAreaForPointPoi(
            pointTitle = selected.place.title,
            pointClass = selected.poiClass,
            pointSubclass = selected.poiSubclass,
            candidates = fallbackCandidates,
        )?.areaOutline
    }

    private fun chooseBestAreaCandidate(candidates: List<AreaCandidate>): AreaCandidate? {
        return candidates.minWithOrNull(AREA_CANDIDATE_COMPARATOR)
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
            ?.replace(NON_TITLE_CHARACTER_REGEX, " ")
            ?.trim()
            ?.replace(WHITESPACE_REGEX, " ")
            ?.ifBlank { null }
    }

    private fun normalizeRawClass(raw: String?): String? {
        return raw
            ?.trim()
            ?.lowercase(LOCALE)
            ?.replace('-', '_')
            ?.ifBlank { null }
    }

    private fun Feature.stringProperty(key: String): String? {
        return runCatching {
            if (hasNonNullValueForProperty(key)) {
                getProperty(key)?.asString?.trim()?.ifBlank { null }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun Feature.doubleProperty(key: String): Double? {
        return runCatching {
            if (hasNonNullValueForProperty(key)) {
                getNumberProperty(key)?.toDouble()
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
            val deltaX = (x - other.x).toDouble()
            val deltaY = (y - other.y).toDouble()
            return deltaX * deltaX + deltaY * deltaY
        }
    }

    private data class PoiCandidate(
        val place: PlaceSummary,
        val geometryPoint: Point,
        val poiClass: String?,
        val poiSubclass: String?,
        val distanceSquared: Double,
    )

    private data class ParsedPointFeature(
        val place: PlaceSummary,
        val poiClass: String?,
        val poiSubclass: String?,
    )

    internal data class AreaCandidate(
        val layerId: String,
        val hasName: Boolean,
        val featureClass: String?,
        val subclass: String?,
        val areaMeters: Double?,
        val place: PlaceSummary,
        val areaOutline: org.maplibre.geojson.FeatureCollection,
    )

    companion object {
        internal const val HIT_TOLERANCE_DP = 20f
        internal const val POI_AREA_FALLBACK_TOLERANCE_DP = 4f
        internal const val POI_AREA_HITBOX_LAYER_ID = "dmap_poi_area_hitbox"
        internal val POI_LAYER_IDS = listOf(
            "poi_transit",
            "poi_z14",
            "poi_z15",
            "poi_z16",
        )
        private val POI_AREA_HITBOX_LAYER_IDS = listOf(
            POI_AREA_HITBOX_LAYER_ID,
        )
        private val LEGACY_AREA_POI_LAYER_IDS = listOf(
            "park",
            "landuse_school",
            "landuse_hospital",
            "landuse_cemetery",
            "landuse_pitch",
            "landuse_track",
        )
        internal val AREA_POI_LAYER_IDS = POI_AREA_HITBOX_LAYER_IDS + LEGACY_AREA_POI_LAYER_IDS

        private val LOCALE = Locale("da", "DK")
        private val NON_TITLE_CHARACTER_REGEX = Regex("[^\\p{L}\\p{Nd}]+")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val GENERIC_AREA_TITLES = setOf(
            "parking",
            "bicycle parking",
            "motorcycle parking",
            "school",
            "hospital",
            "cemetery",
            "pitch",
            "track",
            "park",
            "selected place",
        )
        private val AREA_LAYER_SPECIFICITY = mapOf(
            POI_AREA_HITBOX_LAYER_ID to 0,
            "landuse_school" to 0,
            "landuse_hospital" to 1,
            "landuse_cemetery" to 2,
            "landuse_pitch" to 3,
            "landuse_track" to 4,
            "park" to 5,
        )
        private val AREA_CANDIDATE_COMPARATOR = compareBy<AreaCandidate> {
            if (it.layerId == POI_AREA_HITBOX_LAYER_ID) 0 else 1
        }.thenBy {
            if (it.areaMeters != null) 0 else 1
        }.thenBy {
            it.areaMeters ?: Double.MAX_VALUE
        }.thenBy {
            if (it.hasName) 0 else 1
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
