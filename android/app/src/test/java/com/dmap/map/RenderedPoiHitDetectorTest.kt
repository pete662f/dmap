package com.dmap.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

class RenderedPoiHitDetectorTest {
    private val detector = RenderedPoiHitDetector(density = 1f)

    @Test
    fun `missing name still yields a usable fallback title`() {
        val feature = pointFeature(
            longitude = 12.5683,
            latitude = 55.6761,
            properties = mapOf(
                "class" to "public_transport",
                "subclass" to "tram_stop",
            ),
        )

        val place = detector.parsePointFeature(feature)

        assertNotNull(place)
        assertEquals("Tram Stop", place?.title)
        assertEquals("Tram Stop", place?.categoryHint)
    }

    @Test
    fun `missing optional properties does not crash`() {
        val feature = pointFeature(
            longitude = 10.2039,
            latitude = 56.1629,
            properties = emptyMap(),
        )

        val place = detector.parsePointFeature(feature)

        assertNotNull(place)
        assertEquals("Selected place", place?.title)
        assertNull(place?.subtitle)
        assertNull(place?.categoryHint)
    }

    @Test
    fun `non point geometry is ignored for point parsing`() {
        val feature = Feature.fromJson(
            """
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[12.5683, 55.6761], [12.5690, 55.6765]]
                  },
                  "properties": {
                    "name": "Should be ignored"
                  }
                }
            """.trimIndent(),
        )

        assertNull(detector.parsePointFeature(feature))
    }

    @Test
    fun `deterministic tie break works for same layer candidates`() {
        val secondId = pointFeature(
            longitude = 12.0,
            latitude = 55.0,
            properties = mapOf(
                "osm_type" to "W",
                "osm_id" to "2",
                "name" to "Second",
            ),
        )
        val firstId = pointFeature(
            longitude = 13.0,
            latitude = 55.0,
            properties = mapOf(
                "osm_type" to "W",
                "osm_id" to "1",
                "name" to "First",
            ),
        )

        val selected = detector.chooseBestCandidate(
            tapPoint = RenderedPoiHitDetector.ScreenPoint(0f, 0f),
            features = listOf(secondId, firstId),
            toScreenPoint = { point ->
                when (point.longitude()) {
                    12.0 -> RenderedPoiHitDetector.ScreenPoint(1f, 0f)
                    else -> RenderedPoiHitDetector.ScreenPoint(-1f, 0f)
                }
            },
        )

        assertEquals("W:1", selected?.id)
        assertEquals("First", selected?.title)
    }

    @Test
    fun `parses named park polygon as area poi`() {
        val feature = polygonFeature(
            properties = mapOf(
                "name" to "Botanisk Have",
                "class" to "park",
            ),
        )

        val selected = detector.parseAreaFeature(
            feature = feature,
            layerId = "park",
            tapLatLng = LatLng(55.6869, 12.5738),
        )

        assertNotNull(selected)
        assertEquals("Botanisk Have", selected?.place?.title)
        assertEquals("Park", selected?.place?.categoryHint)
        assertEquals(com.dmap.place.PlaceKind.Poi, selected?.place?.kind)
        assertNotNull(selected?.areaOutline)
    }

    @Test
    fun `parses unnamed public landuse area with fallback title`() {
        val feature = polygonFeature(
            properties = mapOf(
                "class" to "school",
            ),
        )

        val selected = detector.parseAreaFeature(
            feature = feature,
            layerId = "landuse_school",
            tapLatLng = LatLng(55.6761, 12.5683),
        )

        assertNotNull(selected)
        assertEquals("School", selected?.place?.title)
        assertEquals("School", selected?.place?.categoryHint)
    }

    @Test
    fun `parses unnamed poi area hitbox with class fallback title`() {
        val feature = polygonFeature(
            properties = mapOf(
                "class" to "parking",
                "subclass" to "parking",
                "area_m2" to 1000,
            ),
        )

        val selected = detector.parseAreaFeature(
            feature = feature,
            layerId = "dmap_poi_area_hitbox",
            tapLatLng = LatLng(55.6761, 12.5683),
        )

        assertNotNull(selected)
        assertEquals("Parking", selected?.place?.title)
        assertEquals("Parking", selected?.place?.categoryHint)
    }

    @Test
    fun `rejects broad residential landuse`() {
        val feature = polygonFeature(
            properties = mapOf(
                "class" to "residential",
            ),
        )

        val selected = detector.parseAreaFeature(
            feature = feature,
            layerId = "landuse_residential",
            tapLatLng = LatLng(55.6761, 12.5683),
        )

        assertNull(selected)
    }

    @Test
    fun `converts polygon rings to line outline`() {
        val polygon = Polygon.fromLngLats(
            listOf(
                squareRing(12.0, 55.0, 0.04),
                squareRing(12.01, 55.01, 0.01),
            ),
        )

        val outline = AreaOutlineGeometry.fromGeometry(polygon)

        assertNotNull(outline)
        assertEquals(2, outline?.features()?.size)
        outline?.features()?.forEach { feature ->
            assertTrue(feature.geometry() is LineString)
        }
    }

    @Test
    fun `converts multipolygon to line outline`() {
        val multiPolygon = MultiPolygon.fromLngLats(
            listOf(
                listOf(squareRing(12.0, 55.0, 0.01)),
                listOf(squareRing(12.1, 55.1, 0.02)),
            ),
        )

        val outline = AreaOutlineGeometry.fromGeometry(multiPolygon)

        assertNotNull(outline)
        assertEquals(2, outline?.features()?.size)
        outline?.features()?.forEach { feature ->
            assertTrue(feature.geometry() is LineString)
        }
    }

    @Test
    fun `point poi can attach same-name area outline`() {
        val pointFeature = pointFeature(
            longitude = 12.5738,
            latitude = 55.6869,
            properties = mapOf(
                "name" to "Botanisk Have",
                "class" to "park",
            ),
        )
        val areaFeature = polygonFeature(
            properties = mapOf(
                "name" to "Botanisk Have",
                "class" to "park",
            ),
        )

        val selection = detector.selectionFromRenderedFeatures(
            tapPoint = RenderedPoiHitDetector.ScreenPoint(0f, 0f),
            pointFeaturesByLayer = { layerId ->
                if (layerId == "poi_z14") listOf(pointFeature) else emptyList()
            },
            areaFeaturesByLayer = { layerId, _ ->
                if (layerId == "park") listOf(areaFeature) else emptyList()
            },
            tapLatLng = LatLng(55.6869, 12.5738),
            toScreenPoint = { RenderedPoiHitDetector.ScreenPoint(0f, 0f) },
        )

        assertEquals("Botanisk Have", selection?.place?.title)
        assertNotNull(selection?.areaOutline)
    }

    @Test
    fun `parking point poi attaches unnamed parking area by class`() {
        val pointFeature = pointFeature(
            longitude = 12.5738,
            latitude = 55.6869,
            properties = mapOf(
                "class" to "parking",
                "subclass" to "parking",
            ),
        )
        val areaFeature = polygonFeature(
            properties = mapOf(
                "class" to "parking",
                "subclass" to "parking",
                "area_m2" to 1000,
            ),
        )

        val selection = detector.selectionFromRenderedFeatures(
            tapPoint = RenderedPoiHitDetector.ScreenPoint(0f, 0f),
            pointFeaturesByLayer = { layerId ->
                if (layerId == "poi_z14") listOf(pointFeature) else emptyList()
            },
            areaFeaturesByLayer = { layerId, _ ->
                if (layerId == "dmap_poi_area_hitbox") listOf(areaFeature) else emptyList()
            },
            tapLatLng = LatLng(55.6869, 12.5738),
            toScreenPoint = { RenderedPoiHitDetector.ScreenPoint(0f, 0f) },
        )

        assertNotNull(selection)
        assertEquals("Parking", selection?.place?.title)
        assertNotNull(selection?.areaOutline)
    }

    @Test
    fun `generic parking title does not require exact name`() {
        val pointFeature = pointFeature(
            longitude = 12.5738,
            latitude = 55.6869,
            properties = mapOf(
                "class" to "parking",
                "subclass" to "parking",
            ),
        )
        val areaFeature = polygonFeature(
            properties = mapOf(
                "name" to "Different Parking Name",
                "class" to "parking",
                "subclass" to "parking",
                "area_m2" to 1000,
            ),
        )

        val selection = detector.selectionFromRenderedFeatures(
            tapPoint = RenderedPoiHitDetector.ScreenPoint(0f, 0f),
            pointFeaturesByLayer = { layerId ->
                if (layerId == "poi_z14") listOf(pointFeature) else emptyList()
            },
            areaFeaturesByLayer = { layerId, _ ->
                if (layerId == "dmap_poi_area_hitbox") listOf(areaFeature) else emptyList()
            },
            tapLatLng = LatLng(55.6869, 12.5738),
            toScreenPoint = { RenderedPoiHitDetector.ScreenPoint(0f, 0f) },
        )

        assertNotNull(selection)
        assertEquals("Parking", selection?.place?.title)
        assertNotNull(selection?.areaOutline)
    }

    @Test
    fun `hitbox area match does not query legacy area layers`() {
        val pointFeature = pointFeature(
            longitude = 12.5738,
            latitude = 55.6869,
            properties = mapOf(
                "class" to "parking",
                "subclass" to "parking",
            ),
        )
        val hitboxAreaFeature = polygonFeature(
            properties = mapOf(
                "class" to "parking",
                "subclass" to "parking",
                "area_m2" to 1000,
            ),
        )
        val legacyQueries = mutableListOf<String>()

        val selection = detector.selectionFromRenderedFeatures(
            tapPoint = RenderedPoiHitDetector.ScreenPoint(0f, 0f),
            pointFeaturesByLayer = { layerId ->
                if (layerId == "poi_z14") listOf(pointFeature) else emptyList()
            },
            areaFeaturesByLayer = { layerId, _ ->
                if (layerId == "dmap_poi_area_hitbox") {
                    listOf(hitboxAreaFeature)
                } else {
                    legacyQueries += layerId
                    emptyList()
                }
            },
            tapLatLng = LatLng(55.6869, 12.5738),
            toScreenPoint = { RenderedPoiHitDetector.ScreenPoint(0f, 0f) },
        )

        assertNotNull(selection)
        assertNotNull(selection?.areaOutline)
        assertTrue(legacyQueries.isEmpty())
    }

    @Test
    fun `legacy area layers are queried only after hitbox miss`() {
        val pointFeature = pointFeature(
            longitude = 12.5738,
            latitude = 55.6869,
            properties = mapOf(
                "name" to "Botanisk Have",
                "class" to "park",
            ),
        )
        val legacyAreaFeature = polygonFeature(
            properties = mapOf(
                "name" to "Botanisk Have",
                "class" to "park",
            ),
        )
        val areaQueries = mutableListOf<String>()

        val selection = detector.selectionFromRenderedFeatures(
            tapPoint = RenderedPoiHitDetector.ScreenPoint(0f, 0f),
            pointFeaturesByLayer = { layerId ->
                if (layerId == "poi_z14") listOf(pointFeature) else emptyList()
            },
            areaFeaturesByLayer = { layerId, _ ->
                areaQueries += layerId
                if (layerId == "park") listOf(legacyAreaFeature) else emptyList()
            },
            tapLatLng = LatLng(55.6869, 12.5738),
            toScreenPoint = { RenderedPoiHitDetector.ScreenPoint(0f, 0f) },
        )

        assertNotNull(selection)
        assertNotNull(selection?.areaOutline)
        assertEquals("dmap_poi_area_hitbox", areaQueries.first())
        assertTrue("dmap_poi_area_hitbox" in areaQueries)
        assertTrue("park" in areaQueries)
    }

    @Test
    fun `point poi does not attach unrelated area outline`() {
        val pointFeature = pointFeature(
            longitude = 12.5738,
            latitude = 55.6869,
            properties = mapOf(
                "name" to "Cafe Example",
                "class" to "cafe",
            ),
        )
        val areaFeature = polygonFeature(
            properties = mapOf(
                "name" to "Botanisk Have",
                "class" to "park",
            ),
        )

        val selection = detector.selectionFromRenderedFeatures(
            tapPoint = RenderedPoiHitDetector.ScreenPoint(0f, 0f),
            pointFeaturesByLayer = { layerId ->
                if (layerId == "poi_z14") listOf(pointFeature) else emptyList()
            },
            areaFeaturesByLayer = { layerId, _ ->
                if (layerId == "park") listOf(areaFeature) else emptyList()
            },
            tapLatLng = LatLng(55.6869, 12.5738),
            toScreenPoint = { RenderedPoiHitDetector.ScreenPoint(0f, 0f) },
        )

        assertEquals("Cafe Example", selection?.place?.title)
        assertNull(selection?.areaOutline)
    }

    @Test
    fun `area candidates are not selected without a tapped point poi`() {
        val areaFeature = polygonFeature(
            properties = mapOf(
                "name" to "Botanisk Have",
                "class" to "park",
            ),
        )

        val selection = detector.selectionFromRenderedFeatures(
            tapPoint = RenderedPoiHitDetector.ScreenPoint(0f, 0f),
            pointFeaturesByLayer = { emptyList() },
            areaFeaturesByLayer = { layerId, _ ->
                if (layerId == "park") listOf(areaFeature) else emptyList()
            },
            tapLatLng = LatLng(55.6869, 12.5738),
            toScreenPoint = { RenderedPoiHitDetector.ScreenPoint(0f, 0f) },
        )

        assertNull(selection)
    }

    @Test
    fun `area source is still ignored without point poi`() {
        val areaFeature = polygonFeature(
            properties = mapOf(
                "class" to "parking",
                "subclass" to "parking",
                "area_m2" to 1000,
            ),
        )

        val selection = detector.selectionFromRenderedFeatures(
            tapPoint = RenderedPoiHitDetector.ScreenPoint(0f, 0f),
            pointFeaturesByLayer = { emptyList() },
            areaFeaturesByLayer = { layerId, _ ->
                if (layerId == "dmap_poi_area_hitbox") listOf(areaFeature) else emptyList()
            },
            tapLatLng = LatLng(55.6869, 12.5738),
            toScreenPoint = { RenderedPoiHitDetector.ScreenPoint(0f, 0f) },
        )

        assertNull(selection)
    }

    @Test
    fun `tapped point poi attaches same-name area through selection helper`() {
        val pointFeature = pointFeature(
            longitude = 12.5738,
            latitude = 55.6869,
            properties = mapOf(
                "name" to "Botanisk Have",
                "class" to "park",
            ),
        )
        val areaFeature = polygonFeature(
            properties = mapOf(
                "name" to "Botanisk Have",
                "class" to "park",
            ),
        )

        val selection = detector.selectionFromRenderedFeatures(
            tapPoint = RenderedPoiHitDetector.ScreenPoint(0f, 0f),
            pointFeaturesByLayer = { layerId ->
                if (layerId == "poi_z14") listOf(pointFeature) else emptyList()
            },
            areaFeaturesByLayer = { layerId, _ ->
                if (layerId == "park") listOf(areaFeature) else emptyList()
            },
            tapLatLng = LatLng(55.6869, 12.5738),
            toScreenPoint = { RenderedPoiHitDetector.ScreenPoint(0f, 0f) },
        )

        assertNotNull(selection)
        assertEquals("Botanisk Have", selection?.place?.title)
        assertNotNull(selection?.areaOutline)
    }

    @Test
    fun `unrelated containing area is not attached`() {
        val pointFeature = pointFeature(
            longitude = 12.5738,
            latitude = 55.6869,
            properties = mapOf(
                "name" to "Cafe Example",
                "class" to "cafe",
                "subclass" to "cafe",
            ),
        )
        val areaFeature = polygonFeature(
            properties = mapOf(
                "class" to "parking",
                "subclass" to "parking",
                "area_m2" to 1000,
            ),
        )

        val selection = detector.selectionFromRenderedFeatures(
            tapPoint = RenderedPoiHitDetector.ScreenPoint(0f, 0f),
            pointFeaturesByLayer = { layerId ->
                if (layerId == "poi_z14") listOf(pointFeature) else emptyList()
            },
            areaFeaturesByLayer = { layerId, _ ->
                if (layerId == "dmap_poi_area_hitbox") listOf(areaFeature) else emptyList()
            },
            tapLatLng = LatLng(55.6869, 12.5738),
            toScreenPoint = { RenderedPoiHitDetector.ScreenPoint(0f, 0f) },
        )

        assertNotNull(selection)
        assertEquals("Cafe Example", selection?.place?.title)
        assertNull(selection?.areaOutline)
    }

    @Test
    fun `named point prefers same named area over class-only area`() {
        val pointFeature = pointFeature(
            longitude = 12.5738,
            latitude = 55.6869,
            properties = mapOf(
                "name" to "Botanisk Have",
                "class" to "park",
                "subclass" to "park",
            ),
        )
        val classOnlyArea = polygonFeature(
            properties = mapOf(
                "class" to "park",
                "subclass" to "park",
                "area_m2" to 100,
                "osm_type" to "way",
                "osm_id" to "1",
            ),
        )
        val sameNameArea = polygonFeature(
            properties = mapOf(
                "name" to "Botanisk Have",
                "class" to "park",
                "subclass" to "park",
                "area_m2" to 2000,
                "osm_type" to "way",
                "osm_id" to "2",
            ),
        )

        val selectedArea = detector.chooseAreaForPointPoi(
            pointTitle = detector.parsePointFeature(pointFeature)!!.title,
            pointClass = "park",
            pointSubclass = "park",
            candidates = listOf(classOnlyArea, sameNameArea).mapNotNull {
                detector.parseAreaFeature(it, "dmap_poi_area_hitbox", LatLng(55.6869, 12.5738))
            },
        )

        assertNotNull(selectedArea)
        assertEquals("way:2", selectedArea?.place?.id)
    }

    @Test
    fun `smaller area wins among generic class matches`() {
        val pointFeature = pointFeature(
            longitude = 12.5738,
            latitude = 55.6869,
            properties = mapOf(
                "class" to "parking",
                "subclass" to "parking",
            ),
        )
        val largerArea = polygonFeature(
            properties = mapOf(
                "class" to "parking",
                "subclass" to "parking",
                "area_m2" to 2000,
                "osm_type" to "way",
                "osm_id" to "1",
            ),
        )
        val smallerArea = polygonFeature(
            properties = mapOf(
                "class" to "parking",
                "subclass" to "parking",
                "area_m2" to 500,
                "osm_type" to "way",
                "osm_id" to "2",
            ),
        )

        val selectedArea = detector.chooseAreaForPointPoi(
            pointTitle = detector.parsePointFeature(pointFeature)!!.title,
            pointClass = "parking",
            pointSubclass = "parking",
            candidates = listOf(largerArea, smallerArea).mapNotNull {
                detector.parseAreaFeature(it, "dmap_poi_area_hitbox", LatLng(55.6869, 12.5738))
            },
        )

        assertNotNull(selectedArea)
        assertEquals("way:2", selectedArea?.place?.id)
    }

    @Test
    fun `area lookup uses selected poi geometry instead of tap location`() {
        val pointFeature = pointFeature(
            longitude = 12.5738,
            latitude = 55.6869,
            properties = mapOf(
                "name" to "Botanisk Have",
                "class" to "park",
            ),
        )
        val areaFeature = polygonFeature(
            properties = mapOf(
                "name" to "Botanisk Have",
                "class" to "park",
            ),
        )

        val selection = detector.selectionFromRenderedFeatures(
            tapPoint = RenderedPoiHitDetector.ScreenPoint(120f, 40f),
            pointFeaturesByLayer = { layerId ->
                if (layerId == "poi_z14") listOf(pointFeature) else emptyList()
            },
            areaFeaturesByLayer = { layerId, queryPoint ->
                if (
                    layerId == "park" &&
                    queryPoint.longitude() == 12.5738 &&
                    queryPoint.latitude() == 55.6869
                ) {
                    listOf(areaFeature)
                } else {
                    emptyList()
                }
            },
            tapLatLng = LatLng(55.6800, 12.5800),
            toScreenPoint = { RenderedPoiHitDetector.ScreenPoint(120f, 40f) },
        )

        assertNotNull(selection)
        assertEquals("Botanisk Have", selection?.place?.title)
        assertNotNull(selection?.areaOutline)
    }

    @Test
    fun `named poi attaches unnamed matching landuse area by category`() {
        val pointFeature = pointFeature(
            longitude = 10.2039,
            latitude = 56.1629,
            properties = mapOf(
                "name" to "Aarhus Universitetshospital",
                "class" to "hospital",
            ),
        )
        val areaFeature = polygonFeature(
            properties = mapOf(
                "class" to "hospital",
            ),
        )

        val selection = detector.selectionFromRenderedFeatures(
            tapPoint = RenderedPoiHitDetector.ScreenPoint(0f, 0f),
            pointFeaturesByLayer = { layerId ->
                if (layerId == "poi_z14") listOf(pointFeature) else emptyList()
            },
            areaFeaturesByLayer = { layerId, _ ->
                if (layerId == "landuse_hospital") listOf(areaFeature) else emptyList()
            },
            tapLatLng = LatLng(56.1629, 10.2039),
            toScreenPoint = { RenderedPoiHitDetector.ScreenPoint(0f, 0f) },
        )

        assertNotNull(selection)
        assertEquals("Aarhus Universitetshospital", selection?.place?.title)
        assertEquals("Hospital", selection?.place?.categoryHint)
        assertNotNull(selection?.areaOutline)
    }

    private fun pointFeature(
        longitude: Double,
        latitude: Double,
        properties: Map<String, Any?>,
    ): Feature {
        return Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
            properties.forEach { (key, value) ->
                addProperty(key, value)
            }
        }
    }

    private fun polygonFeature(
        properties: Map<String, Any?>,
    ): Feature {
        return Feature.fromGeometry(
            Polygon.fromLngLats(
                listOf(squareRing(12.0, 55.0, 0.02)),
            ),
        ).apply {
            properties.forEach { (key, value) ->
                addProperty(key, value)
            }
        }
    }

    private fun Feature.addProperty(key: String, value: Any?) {
        when (value) {
            null -> Unit
            is Number -> addNumberProperty(key, value)
            is Boolean -> addBooleanProperty(key, value)
            else -> addStringProperty(key, value.toString())
        }
    }

    private fun squareRing(
        longitude: Double,
        latitude: Double,
        size: Double,
    ): List<Point> {
        return listOf(
            Point.fromLngLat(longitude, latitude),
            Point.fromLngLat(longitude + size, latitude),
            Point.fromLngLat(longitude + size, latitude + size),
            Point.fromLngLat(longitude, latitude + size),
            Point.fromLngLat(longitude, latitude),
        )
    }
}
