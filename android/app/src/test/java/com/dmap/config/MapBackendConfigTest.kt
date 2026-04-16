package com.dmap.config

import com.dmap.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapBackendConfigTest {
    @Test
    fun `fromBaseUrl normalizes imagery backend url`() {
        val config = MapBackendConfig.fromBaseUrl(
            baseUrl = " http://localhost:8080/ ",
            searchBaseUrl = " http://localhost:8081/ ",
            routingBaseUrl = " http://localhost:8082/ ",
            imageryBaseUrl = " http://localhost:8083/ ",
        )

        assertEquals("http://localhost:8080", config.baseUrl)
        assertEquals("http://localhost:8081", config.searchBaseUrl)
        assertEquals("http://localhost:8082", config.routingBaseUrl)
        assertEquals("http://localhost:8083", config.imageryBaseUrl)
        assertEquals(
            "http://localhost:8083/ortofoto/tiles/{z}/{x}/{y}.jpg",
            config.ortofotoTileUrl,
        )
    }

    @Test
    fun `fromBaseUrl treats blank optional urls as absent`() {
        val config = MapBackendConfig.fromBaseUrl(
            baseUrl = "http://localhost:8080",
            searchBaseUrl = " ",
            routingBaseUrl = "",
            imageryBaseUrl = " ",
        )

        assertNull(config.searchBaseUrl)
        assertNull(config.routingBaseUrl)
        assertNull(config.imageryBaseUrl)
        assertNull(config.ortofotoTileUrl)
    }

    @Test
    fun `backend provider passes imagery build config into map config`() {
        val config = BackendUrlProvider.fromBuildConfig()

        assertEquals(
            BuildConfig.IMAGERY_BACKEND_URL.trim().removeSuffix("/"),
            config.imageryBaseUrl,
        )
    }
}
