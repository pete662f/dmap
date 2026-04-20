package com.dmap.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationControllerTest {
    @Test
    fun `coarse-only permission is accepted`() {
        assertTrue(
            LocationController.hasAnyLocationPermission(
                finePermissionGranted = false,
                coarsePermissionGranted = true,
            ),
        )
    }

    @Test
    fun `fine permission is accepted`() {
        assertTrue(
            LocationController.hasAnyLocationPermission(
                finePermissionGranted = true,
                coarsePermissionGranted = true,
            ),
        )
    }

    @Test
    fun `denied permissions are rejected`() {
        assertFalse(
            LocationController.hasAnyLocationPermission(
                finePermissionGranted = false,
                coarsePermissionGranted = false,
            ),
        )
    }
}
