package com.dmap.domain

import kotlinx.serialization.Serializable

@Serializable
data class LatLon(val lat: Double, val lon: Double) {
    init {
        require(lat in -90.0..90.0) { "lat out of range: $lat" }
        require(lon in -180.0..180.0) { "lon out of range: $lon" }
    }
}

@Serializable
data class BoundingBox(val south: Double, val west: Double, val north: Double, val east: Double)

@Serializable
data class Address(
    val road: String? = null,
    val houseNumber: String? = null,
    val postcode: String? = null,
    val city: String? = null,
)

@Serializable
data class Contact(
    val phone: String? = null,
    val web: String? = null,
    val email: String? = null,
    val socials: List<String> = emptyList(),
)

/**
 * Where one field of a Place came from, and under what licence.
 *
 * This is the cheapest insurance in the whole plan. OSM is ODbL (share-alike on
 * derived databases), Overture Places is CDLA-Permissive, Rejseplanen is CC BY.
 * The attribution screen is generated from these rows, and retro-fitting
 * provenance onto an already-merged dataset is close to impossible — so it is
 * non-optional from day one.
 */
@Serializable
data class Source(
    val namespace: PlaceId.Namespace,
    val nativeId: String,
    val licence: Licence,
    val fetchedAt: Long,
) {
    @Serializable
    enum class Licence { ODBL, CDLA_PERMISSIVE_2_0, APACHE_2_0, CC_BY_4_0, PUBLIC_DK, USER_CONTRIBUTED }
}

/** How sure we are this place exists where we say it does. */
@Serializable
enum class Confidence {
    /** Geometry from OSM or Overture, name corroborated. */
    HIGH,

    /** Single source, plausible. */
    MEDIUM,

    /** Position derived from an address, not observed — a CVR P-enhed address
     *  is a building, not a shopfront. The UI should show these differently. */
    ADDRESS_DERIVED,
}

@Serializable
enum class Visibility { PUBLIC, PRIVATE }

/**
 * One place. A tapped POI, a search hit and a saved place are all this type.
 *
 * A Place may carry several entries in [sources] — an OSM node conflated with
 * an Overture record and a CVR P-enhed is ONE Place with THREE sources. [id] is
 * chosen by [PlaceId.CANONICAL_PRIORITY].
 */
@Serializable
data class Place(
    val id: PlaceId,
    val name: String,
    val position: LatLon,
    val category: Category = Category.UNKNOWN,
    val altNames: List<String> = emptyList(),
    val address: Address? = null,
    val contact: Contact? = null,
    val openingHours: String? = null,
    val brand: String? = null,
    val sources: List<Source> = emptyList(),
    val confidence: Confidence = Confidence.MEDIUM,
    val visibility: Visibility = Visibility.PUBLIC,
)
