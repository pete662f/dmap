package com.dmap.location

import com.dmap.domain.LatLon
import kotlinx.serialization.Serializable

/**
 * A location fix, with no idea which OS produced it.
 *
 * That is the whole point: Fused Location and Core Location both normalise into
 * this, so the L1–L5 pipeline is platform-free and a trace recorded on Android
 * replays identically on iOS. If this type ever grows a platform-specific
 * field, the shared core has sprung a leak.
 */
@Serializable
data class RawFix(
    /** Monotonic clock, nanoseconds. Android: `elapsedRealtimeNanos`.
     *  iOS: derived from `ProcessInfo.systemUptime`. Never wall clock —
     *  wall clock jumps and would reorder or duplicate fixes. */
    val monotonicNanos: Long,
    val position: LatLon,
    /** Horizontal accuracy in metres (68% confidence radius). */
    val accuracyMeters: Double,
    /** OS-reported speed in m/s, if any. Used only as a sanity check; the
     *  displayed speed comes from the L2 velocity estimate. */
    val speedMetersPerSecond: Double? = null,
    /** OS-reported course in degrees, if any. Unusable below walking pace. */
    val bearingDegrees: Double? = null,
) {
    val monotonicSeconds: Double get() = monotonicNanos / 1_000_000_000.0
}

/** What the pipeline emits, once per display frame. */
@Serializable
data class PuckState(
    val position: LatLon,
    /** Degrees clockwise from true north. */
    val bearingDegrees: Double,
    /** From the L2 velocity estimate, never the OS speed field — the raw value
     *  flickers by several km/h at constant speed. */
    val speedMetersPerSecond: Double,
    val quality: Quality,
    /** True while dead-reckoning through a tunnel or signal loss. The UI should
     *  show this subtly. Never freeze the puck: a frozen puck reads as a
     *  crashed app. */
    val isEstimated: Boolean,
) {
    enum class Quality { GOOD, DEGRADED, LOST }
}
