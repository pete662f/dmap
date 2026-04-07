package com.dmap.map

import org.maplibre.android.geometry.LatLng

object DenmarkCoverage {
    private const val NORTH_LATITUDE = 58.15
    private const val EAST_LONGITUDE = 15.9
    private const val SOUTH_LATITUDE = 54.25
    private const val WEST_LONGITUDE = 7.5

    fun contains(latitude: Double, longitude: Double): Boolean {
        return latitude in SOUTH_LATITUDE..NORTH_LATITUDE &&
            longitude in WEST_LONGITUDE..EAST_LONGITUDE
    }

    fun contains(point: LatLng): Boolean = contains(point.latitude, point.longitude)
}
