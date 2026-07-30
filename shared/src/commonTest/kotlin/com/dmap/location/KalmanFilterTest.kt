package com.dmap.location

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class KalmanFilterTest {

    @Test
    fun converges_on_the_true_speed_of_a_constant_velocity_run() {
        val trace = TraceFixtures.straightMotorway(speedMetersPerSecond = 30.5, noiseMeters = 4.0)
        val filter = KalmanFilter()
        var last: KalmanFilter.Estimate? = null
        trace.fixes.forEach { last = filter.update(it) }

        val speed = last!!.speedMetersPerSecond
        assertTrue(
            abs(speed - 30.5) < 2.0,
            "expected ~30.5 m/s after a minute of convergence, got $speed",
        )
    }

    @Test
    fun reduces_lateral_scatter_relative_to_the_raw_fixes() {
        // The filter's job is to remove noise. Cross-track spread of the
        // filtered track must be clearly smaller than that of the input.
        val trace = TraceFixtures.straightMotorway(noiseMeters = 6.0)
        val origin = trace.fixes.first().position
        val plane = TangentPlane(origin)

        val rawSpread = trace.fixes.map { abs(plane.x(it.position)) }.average()

        val filter = KalmanFilter()
        val filteredSpread = trace.fixes
            .map { abs(plane.x(filter.update(it).position)) }
            .drop(10) // allow convergence
            .average()

        assertTrue(
            filteredSpread < rawSpread,
            "filtered cross-track spread $filteredSpread should be below raw $rawSpread",
        )
    }

    @Test
    fun stationary_device_yields_near_zero_speed() {
        val filter = KalmanFilter()
        var last: KalmanFilter.Estimate? = null
        TraceFixtures.stationary().fixes.forEach { last = filter.update(it) }

        val speed = last!!.speedMetersPerSecond
        assertTrue(speed < 2.0, "parked car should not report $speed m/s")
    }

    @Test
    fun first_fix_is_returned_without_a_lurch() {
        val filter = KalmanFilter()
        val first = TraceFixtures.straightMotorway().fixes.first()
        val estimate = filter.update(first)

        // Initialisation must adopt the fix, not blend it with a default state
        // at (0, 0) — that bug shows up as the puck flying in from the Gulf of
        // Guinea on launch.
        assertTrue(TangentPlane.distanceMeters(first.position, estimate.position) < 1.0)
    }

    @Test
    fun poor_accuracy_is_trusted_less_than_good_accuracy() {
        // Same positional jump, different stated accuracy: the low-confidence
        // fix should move the estimate less.
        fun jumpWith(accuracy: Double): Double {
            val filter = KalmanFilter()
            val trace = TraceFixtures.straightMotorway(seconds = 20, noiseMeters = 0.0)
            trace.fixes.forEach { filter.update(it) }
            val settled = filter.update(trace.fixes.last().copy(monotonicNanos = 21_000_000_000L))

            val offset = trace.fixes.last().let { last ->
                val plane = TangentPlane(last.position)
                last.copy(
                    monotonicNanos = 22_000_000_000L,
                    position = plane.toLatLon(x = 30.0, y = 0.0),
                    accuracyMeters = accuracy,
                )
            }
            val after = filter.update(offset)
            return TangentPlane.distanceMeters(settled.position, after.position)
        }

        val movedWithGoodFix = jumpWith(accuracy = 5.0)
        val movedWithPoorFix = jumpWith(accuracy = 45.0)
        assertTrue(
            movedWithPoorFix < movedWithGoodFix,
            "a 45 m fix moved the estimate $movedWithPoorFix m, a 5 m fix moved it $movedWithGoodFix m",
        )
    }
}
