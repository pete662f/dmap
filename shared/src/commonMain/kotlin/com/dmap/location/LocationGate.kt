package com.dmap.location

/**
 * L1 — throw away bad fixes before they poison the filter.
 *
 * A Kalman filter trusts its inputs in proportion to their stated variance, and
 * GPS lies about variance. One 300 m "accurate" fix dragged through L2 produces
 * a visible lurch that takes seconds to settle, so the cheapest fix is to never
 * let it in.
 *
 * Pure and sequential: feed it a trace, assert the verdicts. No platform, no
 * clock, no I/O.
 */
class LocationGate(
    private val maxAccuracyMeters: Double = 50.0,
    private val maxSpeedMetersPerSecond: Double = 60.0,
    private val lostAfterConsecutiveRejections: Int = 3,
) {
    sealed interface Verdict {
        data class Accepted(val fix: RawFix) : Verdict
        data class Rejected(val fix: RawFix, val reason: Reason, val signalLost: Boolean) : Verdict
    }

    enum class Reason {
        /** Stated accuracy is too poor to be useful while driving. */
        INACCURATE,

        /** Implies a physically impossible jump from the last accepted fix. */
        IMPOSSIBLE_JUMP,

        /** Arrived out of order. Fused Location does this; so does replay. */
        OUT_OF_ORDER,
    }

    private var lastAccepted: RawFix? = null
    private var consecutiveRejections = 0

    val signalLost: Boolean get() = consecutiveRejections >= lostAfterConsecutiveRejections

    fun reset() {
        lastAccepted = null
        consecutiveRejections = 0
    }

    fun accept(fix: RawFix): Verdict {
        val previous = lastAccepted

        if (previous != null && fix.monotonicNanos <= previous.monotonicNanos) {
            return reject(fix, Reason.OUT_OF_ORDER)
        }
        if (fix.accuracyMeters > maxAccuracyMeters) {
            return reject(fix, Reason.INACCURATE)
        }
        if (previous != null) {
            val dt = fix.monotonicSeconds - previous.monotonicSeconds
            val distance = TangentPlane.distanceMeters(previous.position, fix.position)
            if (dt > 0.0 && distance / dt > maxSpeedMetersPerSecond) {
                return reject(fix, Reason.IMPOSSIBLE_JUMP)
            }
        }

        lastAccepted = fix
        consecutiveRejections = 0
        return Verdict.Accepted(fix)
    }

    private fun reject(fix: RawFix, reason: Reason): Verdict.Rejected {
        consecutiveRejections++
        return Verdict.Rejected(fix, reason, signalLost)
    }
}
