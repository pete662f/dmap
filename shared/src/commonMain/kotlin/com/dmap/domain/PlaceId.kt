package com.dmap.domain

import kotlinx.serialization.Serializable

/**
 * The spine of the product. dmap2's single most expensive mistake was that a
 * tapped POI, a search result and a saved place were three unrelated things.
 * Here they are one type with one id, and every feature — search, tap, save,
 * share, route, navigate — speaks this id.
 *
 * Wire format is `"<namespace>:<nativeId>"`. Stable, never regenerated, safe to
 * persist and safe to share. This type is FROZEN as of P0b: changing the format
 * invalidates every saved place, every recent and every queued contribution on
 * both platforms.
 */
@Serializable
data class PlaceId(val namespace: Namespace, val nativeId: String) {

    init {
        require(nativeId.isNotBlank()) { "PlaceId.nativeId must not be blank" }
        require(!nativeId.contains(':')) {
            "PlaceId.nativeId must not contain ':' — it would break round-tripping of $nativeId"
        }
    }

    /** Canonical wire/storage form. */
    override fun toString(): String = "${namespace.prefix}:$nativeId"

    /**
     * Source namespaces, in canonical priority order. When conflation merges
     * several sources into one Place, the winning id is the one from the
     * earliest namespace here — so an OSM-backed place keeps a stable id even
     * as enrichment sources come and go.
     */
    enum class Namespace(val prefix: String) {
        /** OSM node / way / relation, e.g. `osm:n1234567`. */
        OSM("osm"),

        /** Overture GERS id. */
        OVERTURE("ovt"),

        /** CVR P-enhed (physical production unit). */
        CVR("cvr"),

        /** Datafordeleren DAR address UUID. */
        DAR("dar"),

        /** Rejseplanen stop id. */
        REJSEPLANEN("rjp"),

        /** User-created POI — ours. */
        USER("usr"),
        ;

        companion object {
            private val byPrefix = entries.associateBy { it.prefix }

            fun fromPrefix(prefix: String): Namespace? = byPrefix[prefix]
        }
    }

    companion object {
        /** Priority order used by conflation to pick the canonical id. */
        val CANONICAL_PRIORITY: List<Namespace> = listOf(
            Namespace.OSM,
            Namespace.OVERTURE,
            Namespace.CVR,
            Namespace.USER,
            Namespace.DAR,
            Namespace.REJSEPLANEN,
        )

        /** Parses the canonical form. Returns null rather than throwing — this
         *  parses untrusted input from tiles, gateways and saved state. */
        fun parseOrNull(raw: String): PlaceId? {
            val separator = raw.indexOf(':')
            if (separator <= 0 || separator == raw.lastIndex) return null
            val namespace = Namespace.fromPrefix(raw.substring(0, separator)) ?: return null
            val nativeId = raw.substring(separator + 1)
            if (nativeId.isBlank() || nativeId.contains(':')) return null
            return PlaceId(namespace, nativeId)
        }

        /** Picks the canonical id from a conflated set. */
        fun canonical(candidates: Collection<PlaceId>): PlaceId? =
            candidates.minByOrNull { id ->
                CANONICAL_PRIORITY.indexOf(id.namespace).let { if (it < 0) Int.MAX_VALUE else it }
            }
    }
}
