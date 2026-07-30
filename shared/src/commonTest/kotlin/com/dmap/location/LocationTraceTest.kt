package com.dmap.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocationTraceTest {

    @Test
    fun trace_json_round_trips_exactly() {
        // The fixture format is the cross-platform contract, so it has to
        // survive serialisation unchanged on every target.
        val original = TraceFixtures.straightMotorway(seconds = 5)
        val encoded = LocationTrace.json.encodeToString(LocationTrace.serializer(), original)
        val decoded = LocationTrace.parse(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun replay_preserves_fix_order() {
        val trace = TraceFixtures.straightMotorway(seconds = 10)
        val replayed = TraceReplaySource(trace).fixes().toList()

        assertEquals(trace.fixes, replayed)
        assertTrue(replayed.zipWithNext().all { (a, b) -> a.monotonicNanos < b.monotonicNanos })
    }

    @Test
    fun timed_replay_yields_inter_fix_delays() {
        val trace = TraceFixtures.straightMotorway(seconds = 4)
        val timed = TraceReplaySource(trace).timedFixes().toList()

        assertEquals(4, timed.size)
        assertEquals(0L, timed.first().first, "the first fix should not be delayed")
        assertTrue(timed.drop(1).all { it.first == 1000L }, "1 Hz trace should replay at 1000 ms steps")
    }

    @Test
    fun speed_multiplier_compresses_replay() {
        val trace = TraceFixtures.straightMotorway(seconds = 4)
        val fast = TraceReplaySource(trace).timedFixes(speedMultiplier = 4.0).toList()
        assertTrue(fast.drop(1).all { it.first == 250L })
    }
}
