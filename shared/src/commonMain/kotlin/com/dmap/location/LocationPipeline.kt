package com.dmap.location

import com.dmap.domain.LatLon

/**
 * L1 → L2 → (L3 → L4 → L5), composed.
 *
 * L1 and L2 are implemented and tested — those are the P1 exit criteria.
 * L3/L4/L5 land in P3, after the Ferrostar gate (P2.5) tells us how much of L3
 * is a library call and how much is ours. The seams are here now so the P1 work
 * does not have to be rearranged later.
 *
 * Everything in this class is a pure function of the fixes handed to it. There
 * is no clock, no I/O and no platform. That is what makes it testable, and it
 * is why it can be written once for two platforms.
 */
class LocationPipeline(
    private val gate: LocationGate = LocationGate(),
    private val filter: KalmanFilter = KalmanFilter(),
    /** Below this speed, hold the last bearing rather than believing GPS.
     *  Never let the puck spin while stationary at a light. */
    private val bearingHoldBelowMetersPerSecond: Double = 3.0,
    /**
     * How many consecutive samples must exceed [bearingHoldBelowMetersPerSecond]
     * before the course is trusted.
     *
     * Hysteresis, not paranoia: a real vehicle pulling away stays above the
     * threshold for many samples, whereas noise crosses it for one. Requiring a
     * sustained crossing is what makes "the puck must never spin at a
     * standstill" hold even when the filter briefly overestimates speed.
     */
    private val bearingRequiresConsecutiveSamples: Int = 3,
) {
    private var lastBearing: Double = 0.0
    private var haveBearing = false
    private var lastGoodPosition: LatLon? = null
    private var consecutiveMovingSamples = 0

    fun reset() {
        gate.reset()
        filter.reset()
        haveBearing = false
        lastBearing = 0.0
        lastGoodPosition = null
        consecutiveMovingSamples = 0
    }

    /**
     * Feed one raw fix. Returns the new [PuckState], or null if the fix was
     * rejected and there is no prior state to carry forward.
     */
    fun onFix(fix: RawFix): PuckState? {
        when (val verdict = gate.accept(fix)) {
            is LocationGate.Verdict.Rejected -> {
                val position = lastGoodPosition ?: return null
                // Hold the last known state, flagged. L4 will dead-reckon
                // through this once it exists; until then, do not lie about
                // having moved.
                return PuckState(
                    position = position,
                    bearingDegrees = lastBearing,
                    speedMetersPerSecond = 0.0,
                    quality = if (verdict.signalLost) PuckState.Quality.LOST else PuckState.Quality.DEGRADED,
                    isEstimated = true,
                )
            }

            is LocationGate.Verdict.Accepted -> {
                val estimate = filter.update(verdict.fix)
                val speed = estimate.speedMetersPerSecond

                // L5, free-driving branch. The snapped branch — bearing from
                // the route tangent — arrives with L3 in P3 and is the one that
                // makes it feel premium.
                if (speed >= bearingHoldBelowMetersPerSecond) {
                    consecutiveMovingSamples++
                } else {
                    consecutiveMovingSamples = 0
                }

                if (consecutiveMovingSamples >= bearingRequiresConsecutiveSamples) {
                    // Course comes from the filtered velocity vector, not from
                    // two successive positions and not from the OS bearing
                    // field — both are far noisier.
                    val course = courseFromVelocity(
                        estimate.velocityEastMetersPerSecond,
                        estimate.velocityNorthMetersPerSecond,
                    )
                    lastBearing = if (haveBearing) {
                        lastBearing + TangentPlane.shortestArcDegrees(lastBearing, course) * 0.35
                    } else {
                        course
                    }
                    lastBearing = (lastBearing + 360.0) % 360.0
                    haveBearing = true
                }

                lastGoodPosition = estimate.position
                return PuckState(
                    position = estimate.position,
                    bearingDegrees = lastBearing,
                    speedMetersPerSecond = speed,
                    quality = PuckState.Quality.GOOD,
                    isEstimated = false,
                )
            }
        }
    }

    private fun courseFromVelocity(east: Double, north: Double): Double {
        if (east == 0.0 && north == 0.0) return lastBearing
        val degrees = kotlin.math.atan2(east, north) * 180.0 / kotlin.math.PI
        return (degrees + 360.0) % 360.0
    }
}
