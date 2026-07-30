package com.dmap.location

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end over the composed pipeline. These are the assertions that must
 * hold identically on the JVM and on iOS — see `iosTest`.
 */
class LocationPipelineTest {

    @Test
    fun the_puck_never_spins_while_stationary() {
        // The single most noticeable navigation bug: a parked car whose arrow
        // rotates because raw GPS bearing is noise at zero speed.
        val pipeline = LocationPipeline()
        val bearings = TraceFixtures.stationary().fixes.mapNotNull { pipeline.onFix(it)?.bearingDegrees }

        assertTrue(bearings.isNotEmpty())
        assertTrue(
            bearings.all { it == bearings.first() },
            "bearing moved while stationary: ${bearings.distinct().take(5)}",
        )
    }

    @Test
    fun bearing_settles_northbound_on_a_northbound_run() {
        val pipeline = LocationPipeline()
        var last: PuckState? = null
        TraceFixtures.straightMotorway().fixes.forEach { last = pipeline.onFix(it) ?: last }

        val bearing = assertNotNull(last).bearingDegrees
        val offNorth = abs(TangentPlane.shortestArcDegrees(bearing, 0.0))
        assertTrue(offNorth < 15.0, "expected roughly north, got $bearing")
    }

    @Test
    fun signal_loss_degrades_without_freezing_or_teleporting() {
        val pipeline = LocationPipeline()
        val states = TraceFixtures.tunnel().fixes.mapNotNull { pipeline.onFix(it) }

        val estimated = states.filter { it.isEstimated }
        assertTrue(estimated.isNotEmpty(), "the tunnel section should be flagged as estimated")
        assertTrue(
            estimated.any { it.quality == PuckState.Quality.LOST },
            "a 15 s outage should reach LOST, not sit at DEGRADED",
        )
        // And it must come back.
        assertEquals(PuckState.Quality.GOOD, states.last().quality)
        assertTrue(!states.last().isEstimated)
    }

    @Test
    fun rejected_first_fix_produces_no_state_rather_than_a_bogus_one() {
        val pipeline = LocationPipeline()
        val garbage = TraceFixtures.straightMotorway().fixes.first().copy(accuracyMeters = 500.0)
        assertEquals(null, pipeline.onFix(garbage))
    }

    @Test
    fun reset_clears_all_carried_state() {
        val pipeline = LocationPipeline()
        TraceFixtures.straightMotorway().fixes.forEach { pipeline.onFix(it) }
        pipeline.reset()

        val garbage = TraceFixtures.straightMotorway().fixes.first().copy(accuracyMeters = 500.0)
        assertEquals(null, pipeline.onFix(garbage), "state survived a reset")
    }
}
