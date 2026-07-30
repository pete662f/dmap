package com.dmap.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlaceIdTest {

    @Test
    fun round_trips_through_the_wire_format() {
        val id = PlaceId(PlaceId.Namespace.OSM, "n1234567")
        assertEquals("osm:n1234567", id.toString())
        assertEquals(id, PlaceId.parseOrNull(id.toString()))
    }

    @Test
    fun parses_every_namespace() {
        val samples = mapOf(
            "osm:n1234567" to PlaceId.Namespace.OSM,
            "ovt:08f2ab34cd" to PlaceId.Namespace.OVERTURE,
            "cvr:1018234567" to PlaceId.Namespace.CVR,
            "dar:0a3f50a1-b2c3" to PlaceId.Namespace.DAR,
            "rjp:008600626" to PlaceId.Namespace.REJSEPLANEN,
            "usr:9f2c8810" to PlaceId.Namespace.USER,
        )
        samples.forEach { (raw, namespace) ->
            assertEquals(namespace, PlaceId.parseOrNull(raw)?.namespace, "failed on $raw")
        }
    }

    @Test
    fun rejects_malformed_input_without_throwing() {
        // These arrive from tiles, gateways and restored saved state, so parsing
        // must be total.
        listOf("", ":", "osm:", ":123", "osm", "nope:123", "osm:a:b").forEach {
            assertNull(PlaceId.parseOrNull(it), "should not have parsed: '$it'")
        }
    }

    @Test
    fun construction_rejects_ids_that_would_not_round_trip() {
        assertFailsWith<IllegalArgumentException> { PlaceId(PlaceId.Namespace.OSM, "") }
        assertFailsWith<IllegalArgumentException> { PlaceId(PlaceId.Namespace.OSM, "a:b") }
    }

    @Test
    fun canonical_id_follows_source_priority() {
        // An OSM-backed place conflated with Overture and CVR keeps its OSM id,
        // so the id stays stable as enrichment sources come and go.
        val conflated = listOf(
            PlaceId(PlaceId.Namespace.CVR, "1018234567"),
            PlaceId(PlaceId.Namespace.OVERTURE, "08f2ab"),
            PlaceId(PlaceId.Namespace.OSM, "n42"),
        )
        assertEquals(PlaceId(PlaceId.Namespace.OSM, "n42"), PlaceId.canonical(conflated))
    }

    @Test
    fun canonical_falls_through_to_the_best_available_source() {
        val noOsm = listOf(
            PlaceId(PlaceId.Namespace.DAR, "uuid"),
            PlaceId(PlaceId.Namespace.CVR, "1018234567"),
        )
        assertEquals(PlaceId(PlaceId.Namespace.CVR, "1018234567"), PlaceId.canonical(noOsm))
        assertNull(PlaceId.canonical(emptyList()))
    }
}
