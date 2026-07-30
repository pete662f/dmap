package com.dmap.contracts

import com.dmap.domain.BoundingBox
import com.dmap.domain.LatLon
import com.dmap.domain.Place
import com.dmap.domain.PlaceId

/**
 * Every backend seam, declared before the backend exists.
 *
 * Rule 1 from the dmap2 review: each of these ships as `interface` + real impl +
 * [Unavailable] + fake. This is what made Photon a drop-in in dmap2 and it is
 * what will make routing, transit, hazards and identity drop-ins here — on both
 * platforms at once, because the seam lives in commonMain.
 *
 * `Unavailable` is not a stub that pretends to work. It fails honestly with a
 * typed reason so the UI can say "routing is not configured" rather than
 * spinning forever.
 */

/** Typed failure, so the shells can render something useful. */
sealed class ServiceFailure(val detail: String) {
    class NotConfigured(detail: String) : ServiceFailure(detail)
    class Offline(detail: String) : ServiceFailure(detail)
    class Upstream(detail: String) : ServiceFailure(detail)
    class OverBudget(detail: String) : ServiceFailure(detail)
}

sealed interface ServiceResult<out T> {
    data class Ok<out T>(val value: T) : ServiceResult<T>
    data class Failed(val failure: ServiceFailure) : ServiceResult<Nothing>
}

// ---------------------------------------------------------------- search

interface PlaceSearchService {
    suspend fun search(
        query: String,
        near: LatLon? = null,
        limit: Int = 10,
    ): ServiceResult<List<Place>>

    suspend fun reverse(position: LatLon): ServiceResult<Place?>

    class Unavailable(private val why: String = "Place search backend is not configured") :
        PlaceSearchService {
        override suspend fun search(query: String, near: LatLon?, limit: Int) =
            ServiceResult.Failed(ServiceFailure.NotConfigured(why))

        override suspend fun reverse(position: LatLon) =
            ServiceResult.Failed(ServiceFailure.NotConfigured(why))
    }
}

interface PlaceDetailService {
    suspend fun byId(id: PlaceId): ServiceResult<Place?>

    suspend fun inBoundingBox(bounds: BoundingBox, sinceTileWatermark: Long? = null): ServiceResult<List<Place>>

    class Unavailable(private val why: String = "Place detail backend is not configured") :
        PlaceDetailService {
        override suspend fun byId(id: PlaceId) =
            ServiceResult.Failed(ServiceFailure.NotConfigured(why))

        override suspend fun inBoundingBox(bounds: BoundingBox, sinceTileWatermark: Long?) =
            ServiceResult.Failed(ServiceFailure.NotConfigured(why))
    }
}

// ---------------------------------------------------------------- routing

interface RoutingService {
    enum class Mode { CAR, BIKE, FOOT }

    suspend fun route(from: LatLon, to: LatLon, mode: Mode): ServiceResult<Nothing?>

    class Unavailable(private val why: String = "Routing backend is not configured (P3)") :
        RoutingService {
        override suspend fun route(from: LatLon, to: LatLon, mode: Mode) =
            ServiceResult.Failed(ServiceFailure.NotConfigured(why))
    }
}

// ---------------------------------------------------------------- transit

interface TransitService {
    /**
     * Every transit answer carries [realtime] from day one. The Rejseplanen
     * free tier is 50k requests/month and the over-budget behaviour is a
     * DESIGNED MODE, not an outage: journey planning degrades to GTFS static
     * schedule times, clearly labelled "scheduled" instead of "live".
     * Retro-fitting this flag later would touch every screen on two platforms.
     */
    data class Answer<T>(val value: T, val realtime: Boolean)

    suspend fun departures(stopId: PlaceId): ServiceResult<Answer<List<String>>>

    class Unavailable(private val why: String = "Transit backend is not configured (P5)") :
        TransitService {
        override suspend fun departures(stopId: PlaceId) =
            ServiceResult.Failed(ServiceFailure.NotConfigured(why))
    }
}

// ---------------------------------------------------------------- hazards

interface HazardService {
    suspend fun alongRoute(routePolyline: String, corridorMeters: Double = 100.0): ServiceResult<List<Nothing>>

    class Unavailable(private val why: String = "Hazard backend is not configured (P4)") :
        HazardService {
        override suspend fun alongRoute(routePolyline: String, corridorMeters: Double) =
            ServiceResult.Failed(ServiceFailure.NotConfigured(why))
    }
}

// ---------------------------------------------------------------- identity

interface IdentityService {
    /** An opaque, server-issued anonymous device id. Enough for reporter
     *  weighting, rate limits and shadow-bans while storing zero PII. */
    suspend fun deviceId(): ServiceResult<String>

    class Unavailable(private val why: String = "Identity backend is not configured (P4)") :
        IdentityService {
        override suspend fun deviceId() =
            ServiceResult.Failed(ServiceFailure.NotConfigured(why))
    }
}

// ---------------------------------------------------------------- platform ports

/** Keystore on Android, Keychain on iOS. */
interface SecureStore {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
    suspend fun remove(key: String)
}

/** The L0 seam. Native on both sides; [com.dmap.location.TraceReplaySource]
 *  substitutes for it in tests and in demo mode. */
interface LocationSource {
    fun start()
    fun stop()
}
