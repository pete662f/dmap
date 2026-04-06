package com.dmap.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

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

        val place = detector.parseFeature(feature)

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

        val place = detector.parseFeature(feature)

        assertNotNull(place)
        assertEquals("Selected place", place?.title)
        assertNull(place?.subtitle)
        assertNull(place?.categoryHint)
    }

    @Test
    fun `non point geometry is ignored`() {
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

        assertNull(detector.parseFeature(feature))
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
}
