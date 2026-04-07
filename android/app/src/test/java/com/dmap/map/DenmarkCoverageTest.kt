package com.dmap.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DenmarkCoverageTest {
    @Test
    fun `contains returns true for a point inside Denmark`() {
        assertTrue(DenmarkCoverage.contains(latitude = 55.6761, longitude = 12.5683))
    }

    @Test
    fun `contains returns false for a point outside Denmark`() {
        assertFalse(DenmarkCoverage.contains(latitude = 48.8566, longitude = 2.3522))
    }

    @Test
    fun `contains includes the configured bounds edge`() {
        assertTrue(DenmarkCoverage.contains(latitude = 54.25, longitude = 7.5))
        assertTrue(DenmarkCoverage.contains(latitude = 58.15, longitude = 15.9))
        assertFalse(DenmarkCoverage.contains(latitude = 54.2499, longitude = 7.5))
        assertFalse(DenmarkCoverage.contains(latitude = 58.15, longitude = 15.9001))
    }
}
