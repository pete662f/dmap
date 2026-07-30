package com.dmap.location

import com.dmap.domain.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocationGateTest {

    private fun fix(second: Double, lat: Double, lon: Double, accuracy: Double = 8.0) = RawFix(
        monotonicNanos = (second * 1_000_000_000L).toLong(),
        position = LatLon(lat, lon),
        accuracyMeters = accuracy,
    )

    @Test
    fun accepts_a_clean_trace_entirely() {
        val gate = LocationGate()
        val accepted = TraceFixtures.straightMotorway().fixes.count {
            gate.accept(it) is LocationGate.Verdict.Accepted
        }
        assertEquals(60, accepted)
        assertFalse(gate.signalLost)
    }

    @Test
    fun rejects_fixes_less_accurate_than_the_threshold() {
        val gate = LocationGate(maxAccuracyMeters = 50.0)
        assertIs<LocationGate.Verdict.Accepted>(gate.accept(fix(0.0, 56.0, 10.0, accuracy = 49.9)))
        val verdict = gate.accept(fix(1.0, 56.0, 10.0, accuracy = 50.1))
        assertIs<LocationGate.Verdict.Rejected>(verdict)
        assertEquals(LocationGate.Reason.INACCURATE, verdict.reason)
    }

    @Test
    fun rejects_physically_impossible_jumps() {
        val gate = LocationGate(maxSpeedMetersPerSecond = 60.0)
        gate.accept(fix(0.0, 56.0, 10.0))
        // ~1.1 km north in one second is about 1100 m/s.
        val verdict = gate.accept(fix(1.0, 56.01, 10.0))
        assertIs<LocationGate.Verdict.Rejected>(verdict)
        assertEquals(LocationGate.Reason.IMPOSSIBLE_JUMP, verdict.reason)
    }

    @Test
    fun drops_out_of_order_arrivals() {
        val gate = LocationGate()
        gate.accept(fix(5.0, 56.0, 10.0))
        val verdict = gate.accept(fix(4.0, 56.0, 10.0))
        assertIs<LocationGate.Verdict.Rejected>(verdict)
        assertEquals(LocationGate.Reason.OUT_OF_ORDER, verdict.reason)
    }

    @Test
    fun reports_signal_lost_after_three_consecutive_rejections() {
        val gate = LocationGate(lostAfterConsecutiveRejections = 3)
        assertIs<LocationGate.Verdict.Accepted>(gate.accept(fix(0.0, 56.0, 10.0)))

        repeat(2) { assertFalse(gate.signalLost) }
        gate.accept(fix(1.0, 56.0, 10.0, accuracy = 300.0))
        gate.accept(fix(2.0, 56.0, 10.0, accuracy = 300.0))
        assertFalse(gate.signalLost, "two rejections is not yet lost")

        gate.accept(fix(3.0, 56.0, 10.0, accuracy = 300.0))
        assertTrue(gate.signalLost, "three consecutive rejections means signal lost")
    }

    @Test
    fun one_good_fix_clears_the_lost_state() {
        val gate = LocationGate()
        gate.accept(fix(0.0, 56.0, 10.0))
        repeat(3) { gate.accept(fix(1.0 + it, 56.0, 10.0, accuracy = 300.0)) }
        assertTrue(gate.signalLost)

        gate.accept(fix(10.0, 56.0, 10.0))
        assertFalse(gate.signalLost, "recovery must be immediate, not gradual")
    }

    @Test
    fun rejects_the_tunnel_section_and_keeps_the_rest() {
        val gate = LocationGate()
        val verdicts = TraceFixtures.tunnel().fixes.map { gate.accept(it) }
        val rejected = verdicts.filterIsInstance<LocationGate.Verdict.Rejected>()

        // 16 corrupted fixes at 220 m accuracy, all of which must be dropped.
        assertEquals(16, rejected.size)
        assertTrue(rejected.all { it.reason == LocationGate.Reason.INACCURATE })
        assertTrue(verdicts.last() is LocationGate.Verdict.Accepted, "must recover after the tunnel")
    }
}
