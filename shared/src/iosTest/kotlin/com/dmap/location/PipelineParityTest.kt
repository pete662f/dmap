package com.dmap.location

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cross-platform contract, enforced.
 *
 * These assertions are deliberately the SAME assertions as the ones in
 * `commonTest` — they exist separately only so they run on the Kotlin/Native
 * target, where the arithmetic is a different compiler backend and the standard
 * library is a different implementation. A trace recorded on an Android phone
 * must produce the same pipeline output here.
 *
 * If one of these fails while its commonTest twin passes, the shared core has
 * leaked a platform assumption. Do not "fix" it by loosening the tolerance
 * here — find the leak.
 *
 * Run: ./gradlew :shared:iosSimulatorArm64Test
 */
class PipelineParityTest {

    @Test
    fun gate_verdicts_match_the_jvm() {
        val gate = LocationGate()
        val verdicts = TraceFixtures.tunnel().fixes.map { gate.accept(it) }
        val rejected = verdicts.filterIsInstance<LocationGate.Verdict.Rejected>()

        assertEquals(16, rejected.size, "gate rejected a different count than the JVM did")
        assertTrue(verdicts.last() is LocationGate.Verdict.Accepted)
    }

    @Test
    fun kalman_speed_estimate_matches_the_jvm() {
        val filter = KalmanFilter()
        var last: KalmanFilter.Estimate? = null
        TraceFixtures.straightMotorway(speedMetersPerSecond = 30.5).fixes.forEach {
            last = filter.update(it)
        }
        assertTrue(abs(last!!.speedMetersPerSecond - 30.5) < 2.0)
    }

    @Test
    fun puck_does_not_spin_while_stationary() {
        val pipeline = LocationPipeline()
        val bearings = TraceFixtures.stationary().fixes.mapNotNull { pipeline.onFix(it)?.bearingDegrees }
        assertTrue(bearings.all { it == bearings.first() })
    }

    @Test
    fun trace_json_round_trips_on_native() {
        // kotlinx.serialization on Kotlin/Native is a different code path than
        // on the JVM. The fixture format has to survive both identically or the
        // fixtures are not a shared contract.
        val original = TraceFixtures.straightMotorway(seconds = 5)
        val encoded = LocationTrace.json.encodeToString(LocationTrace.serializer(), original)
        assertEquals(original, LocationTrace.parse(encoded))
    }
}
