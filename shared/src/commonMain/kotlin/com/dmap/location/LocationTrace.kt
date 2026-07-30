package com.dmap.location

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A recorded drive, plus what the pipeline is expected to make of it.
 *
 * This is the cross-platform contract. The same JSON is asserted from
 * `commonTest` on the JVM and from the iOS test target through the exported
 * framework: a trace recorded on an Android phone must produce identical
 * pipeline output on iOS. If it does not, the shared core has leaked a platform
 * assumption and CI says so on the day it happens.
 *
 * Record the four scenarios the plan names — motorway, roundabout-heavy
 * suburban, Copenhagen street canyon, and a tunnel — on BOTH devices.
 */
@Serializable
data class LocationTrace(
    val name: String,
    val description: String = "",
    /** Where it was recorded, for when a fixture starts failing and you need to
     *  remember whether it was the phone or the road. */
    val recordedOn: String = "",
    val fixes: List<RawFix>,
) {
    companion object {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        fun parse(raw: String): LocationTrace = json.decodeFromString(raw)
    }
}

/**
 * Replays a trace instead of talking to the OS.
 *
 * Two uses, both load-bearing: it is the test double for every pipeline test,
 * and it is the demo mode — you can show navigation working without driving.
 * Building this in P1, before the navigation UI exists, is the difference
 * between tuning by feel on the motorway and tuning by test.
 */
class TraceReplaySource(private val trace: LocationTrace) {

    /** Every fix, in order, as fast as the caller can take them. */
    fun fixes(): Sequence<RawFix> = trace.fixes.asSequence()

    /**
     * Fixes paired with the wall-clock delay that should precede each one, for
     * replaying in something closer to real time.
     */
    fun timedFixes(speedMultiplier: Double = 1.0): Sequence<Pair<Long, RawFix>> = sequence {
        var previous: RawFix? = null
        for (fix in trace.fixes) {
            val delayMillis = previous?.let {
                ((fix.monotonicNanos - it.monotonicNanos) / 1_000_000.0 / speedMultiplier).toLong()
            } ?: 0L
            yield(delayMillis.coerceAtLeast(0L) to fix)
            previous = fix
        }
    }
}
