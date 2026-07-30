package com.dmap.contracts

import com.dmap.domain.BoundingBox
import com.dmap.domain.LatLon
import com.dmap.domain.PlaceId
import kotlinx.coroutines.flow.Flow

/**
 * The map, as semantic commands and events. One interface, two native
 * implementations (MapLibre Native on Android via `AndroidView`, on iOS via
 * `UIViewRepresentable`).
 *
 * The important design rule: [MapEvent.Tap] carries a [PlaceId], never a screen
 * coordinate or a rendered feature. Resolution happens in the tile schema —
 * Planetiler emits `place_id` on POI features — so the client reads an id and
 * looks it up. That is what replaces dmap2's 548-line hit detector, and what
 * stops us writing that hit detector twice.
 *
 * Nothing here exposes a platform map object. If a method ever needs to, it
 * belongs in the shell, not in this contract.
 */
interface MapController {

    val events: Flow<MapEvent>

    suspend fun setCamera(
        target: LatLon,
        zoom: Double? = null,
        bearingDegrees: Double? = null,
        pitchDegrees: Double? = null,
        animate: Boolean = true,
    )

    suspend fun fitBounds(bounds: BoundingBox, paddingDp: Double = 24.0, animate: Boolean = true)

    suspend fun setBaseLayer(layer: BaseLayer)

    suspend fun setLayerVisible(layerId: String, visible: Boolean)

    /** Feeds a runtime GeoJSON source — the Tier 2 live overlay for user POIs
     *  and anything created since the last tile build. */
    suspend fun setGeoJson(sourceId: String, geoJson: String)

    enum class BaseLayer { VECTOR, ORTOFOTO }
}

sealed interface MapEvent {
    /** A POI carrying a place_id was tapped. Both tile-baked (Tier 1) and
     *  overlay (Tier 2) features produce this — the client cannot tell them
     *  apart, which is the point. */
    data class Tap(val placeId: PlaceId) : MapEvent

    /** The map was tapped where no place is. Does NOT clear selection and does
     *  NOT snap to something nearby — carried forward from dmap2's M2 behaviour. */
    data class TapEmpty(val position: LatLon) : MapEvent

    /** Long press drops a pin at the exact pressed coordinate. */
    data class LongPress(val position: LatLon) : MapEvent

    data class CameraIdle(val center: LatLon, val zoom: Double, val bounds: BoundingBox) : MapEvent

    data object StyleLoaded : MapEvent

    data class Failed(val message: String) : MapEvent
}
