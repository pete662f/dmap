package com.dmap.location

import com.dmap.domain.LatLon
import kotlin.math.sqrt

/**
 * L2 — a constant-velocity Kalman filter over a local tangent plane.
 *
 * Output is a smoothed position AND a velocity estimate. The velocity is the
 * part that matters most: L4 needs it to dead-reckon between fixes, and the
 * speed readout uses it because the OS speed field flickers by several km/h at
 * constant speed.
 *
 * East and north are filtered independently. Measurement noise (GPS accuracy)
 * and process noise (driver acceleration) are both isotropic here, so the
 * cross-axis covariance terms stay at zero and two 2-state filters are exactly
 * equivalent to one 4-state filter with block-diagonal matrices — for a lot
 * less arithmetic and much easier testing.
 *
 * NOTE: this removes noise, not the 1 Hz stepping. Stopping here is why people
 * conclude "GPS is just jumpy". L4 is what makes it look like a car.
 */
class KalmanFilter(
    /** Process noise: expected acceleration variance, m²/s⁴. Higher tracks
     *  aggressive driving faster and smooths less. ~1.0 is a sane car default. */
    private val accelerationVariance: Double = 1.0,
    /**
     * Floor on the cold-start velocity uncertainty, m/s. See
     * [initialVelocityVarianceFor] for why this matters so much.
     */
    private val minimumSpeedUncertaintyMetersPerSecond: Double = 3.0,
) {
    /**
     * How uncertain the velocity estimate is before any motion is observed —
     * and the single most consequential constant in this class.
     *
     * A cold-start filter cannot establish velocity from one fix, so its first
     * velocity estimate is dominated by one noise sample. How badly depends
     * entirely on the prior: with an effectively infinite prior (1e6 m²/s²) the
     * velocity Kalman gain is ~0.98, meaning the filter attributes the whole
     * position innovation to real movement. A parked car's second fix then came
     * out at 9.5 m/s — enough to cross the L5 bearing threshold and flick the
     * puck to a random heading on launch. Note that this is not a bug in the
     * arithmetic: given "velocity could be anything", 9 m of movement in 1 s
     * really is best explained as 9 m/s.
     *
     * Clamping to a physical ceiling (60 m/s, as L1's jump gate uses) does not
     * help — 3600 is still huge next to a position variance of ~36.
     *
     * So seed from the OS-reported speed instead. Doppler-derived GPS speed is
     * far better than differencing positions, and both platforms provide it.
     * The plan's rule that the *displayed* speed must come from this filter
     * rather than `Location.getSpeed()` is about display; using the reported
     * value as a Bayesian prior is a different question and the answer is yes.
     *
     * When no speed is reported, assume slow: a cold start is much more often a
     * stationary phone than a moving car, and the filter widens on its own
     * through process noise if that guess was wrong.
     */
    private fun initialVelocityVarianceFor(fix: RawFix): Double {
        val reported = fix.speedMetersPerSecond ?: 0.0
        val uncertainty = maxOf(reported * 1.5, minimumSpeedUncertaintyMetersPerSecond)
        return uncertainty * uncertainty
    }

    private var plane: TangentPlane? = null
    private var east = Axis()
    private var north = Axis()
    private var lastSeconds = 0.0
    private var initialised = false

    data class Estimate(
        val position: LatLon,
        val velocityEastMetersPerSecond: Double,
        val velocityNorthMetersPerSecond: Double,
    ) {
        val speedMetersPerSecond: Double
            get() = sqrt(
                velocityEastMetersPerSecond * velocityEastMetersPerSecond +
                    velocityNorthMetersPerSecond * velocityNorthMetersPerSecond,
            )
    }

    fun reset() {
        plane = null
        east = Axis()
        north = Axis()
        initialised = false
    }

    /** Feed one L1-accepted fix. Returns the smoothed estimate. */
    fun update(fix: RawFix): Estimate {
        val variance = (fix.accuracyMeters * fix.accuracyMeters).coerceAtLeast(1.0)

        var currentPlane = plane
        if (!initialised || currentPlane == null || currentPlane.shouldReanchor(fix.position)) {
            val firstEver = !initialised
            currentPlane = TangentPlane(fix.position)
            plane = currentPlane
            // A re-anchor rebases position onto the new origin but KEEPS the
            // velocity estimate and its covariance, so it is invisible
            // downstream. A cold start has no velocity to keep, and gets the
            // bounded prior instead.
            val coldStartVariance = initialVelocityVarianceFor(fix)
            val velocityVarianceEast = if (firstEver) coldStartVariance else east.vv
            val velocityVarianceNorth = if (firstEver) coldStartVariance else north.vv
            east = Axis(position = 0.0, velocity = east.velocity, pp = variance, pv = 0.0, vv = velocityVarianceEast)
            north = Axis(position = 0.0, velocity = north.velocity, pp = variance, pv = 0.0, vv = velocityVarianceNorth)
            lastSeconds = fix.monotonicSeconds
            initialised = true
            return estimate(currentPlane)
        }

        val dt = (fix.monotonicSeconds - lastSeconds).coerceAtLeast(0.0)
        lastSeconds = fix.monotonicSeconds

        east.predict(dt, accelerationVariance)
        north.predict(dt, accelerationVariance)
        east.correct(currentPlane.x(fix.position), variance)
        north.correct(currentPlane.y(fix.position), variance)

        return estimate(currentPlane)
    }

    private fun estimate(plane: TangentPlane) = Estimate(
        position = plane.toLatLon(east.position, north.position),
        velocityEastMetersPerSecond = east.velocity,
        velocityNorthMetersPerSecond = north.velocity,
    )

    /**
     * One axis: state [position, velocity], covariance [[pp, pv], [pv, vv]].
     */
    private class Axis(
        var position: Double = 0.0,
        var velocity: Double = 0.0,
        var pp: Double = 1e6,
        var pv: Double = 0.0,
        var vv: Double = 1e6,
    ) {
        /** x' = F x, P' = F P Fᵀ + Q, with F = [[1, dt], [0, 1]] and Q the
         *  standard continuous-acceleration discretisation. */
        fun predict(dt: Double, accelerationVariance: Double) {
            position += velocity * dt
            val nextPp = pp + 2.0 * dt * pv + dt * dt * vv
            val nextPv = pv + dt * vv
            pp = nextPp + accelerationVariance * dt * dt * dt / 3.0
            pv = nextPv + accelerationVariance * dt * dt / 2.0
            vv += accelerationVariance * dt
        }

        /** Scalar position measurement with variance [measurementVariance]. */
        fun correct(measurement: Double, measurementVariance: Double) {
            val innovation = measurement - position
            val s = pp + measurementVariance
            if (s <= 0.0) return
            val kp = pp / s
            val kv = pv / s
            position += kp * innovation
            velocity += kv * innovation
            val priorPp = pp
            val priorPv = pv
            pp -= kp * priorPp
            pv -= kp * priorPv
            vv -= kv * priorPv
        }
    }
}
