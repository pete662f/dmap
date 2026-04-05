package com.dmap.services.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class Polyline6Test {
    @Test
    fun `decode returns expected coordinates`() {
        val coordinates = Polyline6.decode("gkeeiBwmb~VouSooB")

        assertEquals(2, coordinates.size)
        assertEquals(55.6761, coordinates[0].latitude, 0.000001)
        assertEquals(12.5683, coordinates[0].longitude, 0.000001)
        assertEquals(55.6867, coordinates[1].latitude, 0.000001)
        assertEquals(12.5701, coordinates[1].longitude, 0.000001)
    }
}
