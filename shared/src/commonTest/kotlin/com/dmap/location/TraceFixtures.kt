package com.dmap.location

import com.dmap.domain.LatLon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

/**
 * Synthetic traces, so the pipeline has something to be tested against before
 * real Danish drives are recorded in P1.
 *
 * These are NOT a substitute for the real fixtures. A synthetic trace has
 * Gaussian noise and honest accuracy numbers; real GPS has correlated
 * multipath error in a street canyon and lies about accuracy in a tunnel. The
 * P1 job is to record motorway, roundabout, Copenhagen canyon and tunnel on
 * both devices and commit them next to this file.
 */
object TraceFixtures {

    /** Roughly the E45 north of Aarhus, heading north at 110 km/h. */
    fun straightMotorway(
        seconds: Int = 60,
        speedMetersPerSecond: Double = 30.5,
        accuracyMeters: Double = 8.0,
        noiseMeters: Double = 4.0,
        seed: Int = 1,
    ): LocationTrace {
        val random = Random(seed)
        val start = LatLon(56.2000, 10.2000)
        val plane = TangentPlane(start)
        val fixes = (0 until seconds).map { second ->
            val alongTrack = speedMetersPerSecond * second
            RawFix(
                monotonicNanos = second.toLong() * 1_000_000_000L,
                position = plane.toLatLon(
                    x = random.nextGaussianish() * noiseMeters,
                    y = alongTrack + random.nextGaussianish() * noiseMeters,
                ),
                accuracyMeters = accuracyMeters,
                speedMetersPerSecond = speedMetersPerSecond,
            )
        }
        return LocationTrace(
            name = "synthetic-motorway",
            description = "Straight northbound run at ${speedMetersPerSecond.toInt()} m/s with Gaussian noise",
            recordedOn = "synthetic",
            fixes = fixes,
        )
    }

    /** A stationary device — the "puck spins at a red light" regression. */
    fun stationary(seconds: Int = 30, accuracyMeters: Double = 6.0, seed: Int = 2): LocationTrace {
        val random = Random(seed)
        val at = LatLon(55.6761, 12.5683)
        val plane = TangentPlane(at)
        return LocationTrace(
            name = "synthetic-stationary",
            description = "Parked. Bearing must not rotate and speed must stay near zero.",
            recordedOn = "synthetic",
            fixes = (0 until seconds).map { second ->
                RawFix(
                    monotonicNanos = second.toLong() * 1_000_000_000L,
                    position = plane.toLatLon(
                        x = random.nextGaussianish() * 3.0,
                        y = random.nextGaussianish() * 3.0,
                    ),
                    accuracyMeters = accuracyMeters,
                    // A stationary GPS reports a wandering nonsense bearing.
                    // L5 must ignore it below the speed threshold.
                    bearingDegrees = random.nextDouble() * 360.0,
                )
            },
        )
    }

    /** Motorway run with a 15 s gap and garbage fixes in the middle — the
     *  tunnel case. */
    fun tunnel(seed: Int = 3): LocationTrace {
        val clean = straightMotorway(seconds = 60, seed = seed)
        val corrupted = clean.fixes.map { fix ->
            if (fix.monotonicNanos in 20_000_000_000L..35_000_000_000L) {
                fix.copy(accuracyMeters = 220.0)
            } else {
                fix
            }
        }
        return clean.copy(
            name = "synthetic-tunnel",
            description = "Accuracy collapses for 15 s. L1 must reject; the puck must not freeze or teleport.",
            fixes = corrupted,
        )
    }

    /** Box-Muller, so fixtures do not depend on a platform RNG's distribution. */
    private fun Random.nextGaussianish(): Double {
        val u1 = nextDouble().coerceAtLeast(1e-12)
        val u2 = nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * cos(2.0 * PI * u2)
    }
}
