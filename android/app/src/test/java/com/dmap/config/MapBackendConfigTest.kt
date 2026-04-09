package com.dmap.config

import org.junit.Assert.assertEquals
import org.junit.Test

class MapBackendConfigTest {
    @Test
    fun `style url includes asset version query`() {
        val config = MapBackendConfig.fromBaseUrl(
            baseUrl = "http://localhost:8080/",
            styleAssetVersion = "abc123",
        )

        assertEquals(
            "http://localhost:8080/styles/osm-liberty/style.json?v=abc123",
            config.styleUrl,
        )
    }
}
