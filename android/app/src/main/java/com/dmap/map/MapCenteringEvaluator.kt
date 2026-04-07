package com.dmap.map

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object MapCenteringEvaluator {
    private const val ENTER_THRESHOLD_METERS = 10.0
    private const val EXIT_THRESHOLD_METERS = 15.0
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun evaluate(
        wasCenteredOnUser: Boolean,
        cameraLatitude: Double,
        cameraLongitude: Double,
        userLatitude: Double,
        userLongitude: Double,
    ): Boolean {
        val distanceMeters = distanceMeters(
            startLatitude = cameraLatitude,
            startLongitude = cameraLongitude,
            endLatitude = userLatitude,
            endLongitude = userLongitude,
        )
        return if (wasCenteredOnUser) {
            distanceMeters <= EXIT_THRESHOLD_METERS
        } else {
            distanceMeters <= ENTER_THRESHOLD_METERS
        }
    }

    private fun distanceMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
    ): Double {
        val latitudeDelta = Math.toRadians(endLatitude - startLatitude)
        val longitudeDelta = Math.toRadians(endLongitude - startLongitude)
        val startLatitudeRadians = Math.toRadians(startLatitude)
        val endLatitudeRadians = Math.toRadians(endLatitude)

        val haversine = sin(latitudeDelta / 2).pow(2) +
            cos(startLatitudeRadians) * cos(endLatitudeRadians) * sin(longitudeDelta / 2).pow(2)
        val angularDistance = 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
        return EARTH_RADIUS_METERS * angularDistance
    }
}
