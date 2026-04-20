package com.dmap.services.search

import com.dmap.place.PlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotonResponseParserTest {
    @Test
    fun `parse builds readable POI result`() {
        val payload = """
            {
              "features": [
                {
                  "geometry": {
                    "coordinates": [12.5683, 55.6761]
                  },
                  "properties": {
                    "name": "TorvehallerneKBH",
                    "street": "Frederiksborggade",
                    "housenumber": "21",
                    "postcode": "1360",
                    "city": "København",
                    "country": "Danmark",
                    "type": "house",
                    "osm_type": "N",
                    "osm_id": "123",
                    "osm_key": "amenity",
                    "osm_value": "marketplace"
                  }
                }
              ]
            }
        """.trimIndent()

        val results = PhotonResponseParser.parse(payload)

        assertEquals(1, results.size)
        val place = results.first().place
        assertEquals("TorvehallerneKBH", place.title)
        assertEquals("Frederiksborggade 21, 1360 København", place.subtitle)
        assertEquals(PlaceKind.Address, place.kind)
        assertEquals("Marketplace", place.categoryHint)
    }

    @Test
    fun `parse falls back to dropped pin title when needed`() {
        val payload = """
            {
              "features": [
                {
                  "geometry": {
                    "coordinates": [10.2039, 56.1629]
                  },
                  "properties": {
                    "postcode": "8000",
                    "city": "Aarhus",
                    "country": "Danmark",
                    "osm_type": "W",
                    "osm_id": "456",
                    "type": "street"
                  }
                }
              ]
            }
        """.trimIndent()

        val results = PhotonResponseParser.parse(payload, fallbackTitle = "Dropped pin")

        assertEquals(1, results.size)
        val place = results.first().place
        assertEquals("Aarhus", place.title)
        assertEquals("8000 Aarhus", place.subtitle)
        assertEquals(PlaceKind.Street, place.kind)
    }

    @Test
    fun `parse ignores malformed features`() {
        val payload = """
            {
              "features": [
                {
                  "geometry": {
                    "coordinates": []
                  },
                  "properties": {
                    "name": "Broken"
                  }
                }
              ]
            }
        """.trimIndent()

        val results = PhotonResponseParser.parse(payload)

        assertEquals(0, results.size)
    }

    @Test
    fun `parse ignores json null and non-string labels`() {
        val payload = """
            {
              "features": [
                {
                  "geometry": {
                    "coordinates": [12.5683, 55.6761]
                  },
                  "properties": {
                    "name": null,
                    "street": true,
                    "postcode": {"value": "1360"},
                    "city": "København",
                    "country": "Danmark",
                    "type": "city"
                  }
                }
              ]
            }
        """.trimIndent()

        val results = PhotonResponseParser.parse(payload)

        assertEquals(1, results.size)
        val place = results.first().place
        assertEquals("København", place.title)
        assertNull(place.subtitle)
    }

    @Test
    fun `parse decodes escaped json strings`() {
        val payload = """
            {
              "features": [
                {
                  "geometry": {
                    "coordinates": [12.5683, 55.6761]
                  },
                  "properties": {
                    "name": "Joe\u0027s Caf\u00e9",
                    "city": "K\u00f8benhavn",
                    "country": "Danmark",
                    "osm_type": "N",
                    "osm_id": "123",
                    "osm_key": "amenity",
                    "osm_value": "cafe"
                  }
                }
              ]
            }
        """.trimIndent()

        val results = PhotonResponseParser.parse(payload)

        assertEquals(1, results.size)
        val place = results.first().place
        assertEquals("Joe's Café", place.title)
        assertEquals("København", place.subtitle)
    }

    @Test
    fun `parse returns empty list when features is not an array`() {
        val payload = """{"features": {"unexpected": true}}"""

        val results = PhotonResponseParser.parse(payload)

        assertEquals(0, results.size)
    }

    @Test
    fun `parse skips malformed feature and keeps valid feature`() {
        val payload = """
            {
              "features": [
                "not an object",
                {
                  "geometry": {
                    "coordinates": []
                  },
                  "properties": {
                    "name": "Broken"
                  }
                },
                {
                  "geometry": {
                    "coordinates": [10.2039, 56.1629]
                  },
                  "properties": {
                    "name": "Aarhus",
                    "country": "Danmark",
                    "type": "city"
                  }
                }
              ]
            }
        """.trimIndent()

        val results = PhotonResponseParser.parse(payload)

        assertEquals(1, results.size)
        assertEquals("Aarhus", results.first().place.title)
    }
}
