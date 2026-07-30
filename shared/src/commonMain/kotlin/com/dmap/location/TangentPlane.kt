package com.dmap.location

import com.dmap.domain.LatLon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * A local east-north tangent plane in metres, anchored at [origin].
 *
 * Filtering degrees is the classic implementation bug: a degree of longitude is
 * ~111 km at the equator and ~62 km in Denmark, so a filter tuned on raw
 * lat/lon is anisotropic and wrong. Everything numeric in this package works in
 * metres on one of these, re-anchored as the vehicle moves.
 */
class TangentPlane(val origin: LatLon) {

    private val metersPerDegreeLat = 111_132.0
    private val metersPerDegreeLon = 111_320.0 * cos(origin.lat * PI / 180.0)

    /** Metres east of origin. */
    fun x(position: LatLon): Double = (position.lon - origin.lon) * metersPerDegreeLon

    /** Metres north of origin. */
    fun y(position: LatLon): Double = (position.lat - origin.lat) * metersPerDegreeLat

    fun toLatLon(x: Double, y: Double): LatLon = LatLon(
        lat = origin.lat + y / metersPerDegreeLat,
        lon = origin.lon + x / metersPerDegreeLon,
    )

    /** Re-anchor once the vehicle has moved far enough that the flat-earth
     *  approximation starts to cost accuracy. A few km is plenty. */
    fun shouldReanchor(position: LatLon, thresholdMeters: Double = 5_000.0): Boolean =
        abs(x(position)) > thresholdMeters || abs(y(position)) > thresholdMeters

    companion object {
        /** Great-circle-ish distance, good to a fraction of a percent over the
         *  distances this app cares about. */
        fun distanceMeters(from: LatLon, to: LatLon): Double {
            val plane = TangentPlane(from)
            val dx = plane.x(to)
            val dy = plane.y(to)
            return sqrt(dx * dx + dy * dy)
        }

        /** Initial bearing from [from] to [to], degrees clockwise from north. */
        fun bearingDegrees(from: LatLon, to: LatLon): Double {
            val plane = TangentPlane(from)
            val degrees = atan2(plane.x(to), plane.y(to)) * 180.0 / PI
            return (degrees + 360.0) % 360.0
        }

        /** Shortest signed angular difference in degrees, in (-180, 180].
         *  This is what stops a heading taking the long way round 0°/360°. */
        fun shortestArcDegrees(from: Double, to: Double): Double {
            var delta = (to - from) % 360.0
            if (delta > 180.0) delta -= 360.0
            if (delta <= -180.0) delta += 360.0
            return delta
        }
    }
}
