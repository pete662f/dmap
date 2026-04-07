package com.dmap.map

import kotlin.math.PI
import kotlin.math.cos
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCenteringEvaluatorTest {
    @Test
    fun `identical coordinates are centered`() {
        assertTrue(
            MapCenteringEvaluator.evaluate(
                wasCenteredOnUser = false,
                cameraLatitude = 55.6761,
                cameraLongitude = 12.5683,
                userLatitude = 55.6761,
                userLongitude = 12.5683,
            ),
        )
    }

    @Test
    fun `distance within enter threshold becomes centered`() {
        val latitude = 55.6761
        val longitude = 12.5683

        assertTrue(
            MapCenteringEvaluator.evaluate(
                wasCenteredOnUser = false,
                cameraLatitude = latitude,
                cameraLongitude = longitude,
                userLatitude = latitude,
                userLongitude = longitude + longitudeOffsetDegrees(
                    meters = 9.0,
                    atLatitude = latitude,
                ),
            ),
        )
    }

    @Test
    fun `distance between thresholds preserves previous state`() {
        val latitude = 55.6761
        val longitude = 12.5683
        val shiftedLongitude = longitude + longitudeOffsetDegrees(
            meters = 12.0,
            atLatitude = latitude,
        )

        assertFalse(
            MapCenteringEvaluator.evaluate(
                wasCenteredOnUser = false,
                cameraLatitude = latitude,
                cameraLongitude = longitude,
                userLatitude = latitude,
                userLongitude = shiftedLongitude,
            ),
        )
        assertTrue(
            MapCenteringEvaluator.evaluate(
                wasCenteredOnUser = true,
                cameraLatitude = latitude,
                cameraLongitude = longitude,
                userLatitude = latitude,
                userLongitude = shiftedLongitude,
            ),
        )
    }

    @Test
    fun `distance above exit threshold is not centered`() {
        val latitude = 55.6761
        val longitude = 12.5683

        assertFalse(
            MapCenteringEvaluator.evaluate(
                wasCenteredOnUser = true,
                cameraLatitude = latitude,
                cameraLongitude = longitude,
                userLatitude = latitude,
                userLongitude = longitude + longitudeOffsetDegrees(
                    meters = 16.0,
                    atLatitude = latitude,
                ),
            ),
        )
    }

    @Test
    fun `clearly different coordinates are not centered`() {
        assertFalse(
            MapCenteringEvaluator.evaluate(
                wasCenteredOnUser = false,
                cameraLatitude = 55.6761,
                cameraLongitude = 12.5683,
                userLatitude = 56.1629,
                userLongitude = 10.2039,
            ),
        )
    }

    private fun longitudeOffsetDegrees(
        meters: Double,
        atLatitude: Double,
    ): Double {
        val metersPerDegreeLongitude = 111_320.0 * cos(atLatitude * PI / 180.0)
        return meters / metersPerDegreeLongitude
    }
}
