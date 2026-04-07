package com.dmap.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MapPresentationConfigTest {
    @Test
    fun `denmark presentation keeps Denmark-first defaults and allows global zoom out`() {
        val config = MapPresentationConfig.denmark()
        val target = config.defaultCamera.target

        assertNotNull(target)
        assertEquals(56.1725, target!!.latitude, 0.0)
        assertEquals(10.0938, target.longitude, 0.0)
        assertEquals(6.15, config.defaultCamera.zoom, 0.0)
        assertEquals(1.0, config.minZoom, 0.0)
        assertNull(config.cameraBounds)
    }
}
