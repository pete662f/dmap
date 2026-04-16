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
        val pointPlace = detector.parsePointFeature(
            pointFeature(
                longitude = 12.5738,
                latitude = 55.6869,
                properties = mapOf(
                    "name" to "Botanisk Have",
                    "class" to "park",
                ),
            ),
        )
        val area = detector.parseAreaFeature(
            feature = polygonFeature(
                properties = mapOf(
                    "name" to "Botanisk Have",
                    "class" to "park",
                ),
            ),
            layerId = "park",
            tapLatLng = LatLng(55.6869, 12.5738),
        )

        val selection = RenderedPoiSelection(
            place = pointPlace!!,
            areaOutline = detector.chooseMatchingAreaCandidate(
                title = pointPlace.title,
                candidates = listOf(area!!),
            )?.areaOutline,
        )

        assertEquals("Botanisk Have", selection.place.title)
        assertNotNull(selection.areaOutline)
    }

    @Test
    fun `point poi does not attach unrelated area outline`() {
        val pointPlace = detector.parsePointFeature(
            pointFeature(
                longitude = 12.5738,
                latitude = 55.6869,
                properties = mapOf(
                    "name" to "Cafe Example",
                    "class" to "cafe",
                ),
            ),
        )
        val area = detector.parseAreaFeature(
            feature = polygonFeature(
                properties = mapOf(
                    "name" to "Botanisk Have",
                    "class" to "park",
                ),
            ),
            layerId = "park",
            tapLatLng = LatLng(55.6869, 12.5738),
        )

        val selection = RenderedPoiSelection(
            place = pointPlace!!,
            areaOutline = detector.chooseMatchingAreaCandidate(
                title = pointPlace.title,
                candidates = listOf(area!!),
            )?.areaOutline,
        )

        assertEquals("Cafe Example", selection.place.title)
        assertNull(selection.areaOutline)
    }

    private fun pointFeature(
        longitude: Double,
        latitude: Double,
        properties: Map<String, String>,
    ): Feature {
        return Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
            properties.forEach { (key, value) ->
                addStringProperty(key, value)
            }
        }
    }

    private fun polygonFeature(
        properties: Map<String, String>,
    ): Feature {
        return Feature.fromGeometry(
            Polygon.fromLngLats(
                listOf(squareRing(12.0, 55.0, 0.02)),
            ),
        ).apply {
            properties.forEach { (key, value) ->
                addStringProperty(key, value)
            }
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
