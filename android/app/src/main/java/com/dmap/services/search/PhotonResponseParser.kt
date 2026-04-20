package com.dmap.services.search

import com.dmap.place.PlaceKind
import com.dmap.place.PlaceSummary
import com.dmap.place.SearchResult
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

internal object PhotonResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(
        payload: String,
        fallbackTitle: String? = null,
    ): List<SearchResult> {
        val root = runCatching { json.parseToJsonElement(payload).objectOrNull() }.getOrNull() ?: return emptyList()
        val features = root["features"].arrayOrNull() ?: JsonArray(emptyList())

        return features.mapNotNull { feature ->
            parseFeature(feature.objectOrNull() ?: return@mapNotNull null, fallbackTitle)
        }.distinctBy { result ->
            listOf(
                result.place.id,
                result.place.title,
                result.place.subtitle,
                result.place.latitude.toString(),
                result.place.longitude.toString(),
            ).joinToString("|")
        }
    }

    private fun parseFeature(
        feature: JsonObject,
        fallbackTitle: String?,
    ): SearchResult? {
        val coordinates = feature["geometry"].objectOrNull()?.get("coordinates").arrayOrNull() ?: return null
        if (coordinates.size < 2) return null

        val longitude = coordinates[0].doubleOrNull() ?: return null
        val latitude = coordinates[1].doubleOrNull() ?: return null
        val properties = feature["properties"].objectOrNull() ?: JsonObject(emptyMap())

        val type = properties.string("type")
        val osmKey = properties.string("osm_key")
        val osmValue = properties.string("osm_value")
        val kind = PlaceKind.fromPhoton(
            type = type,
            osmKey = osmKey,
            osmValue = osmValue,
        )

        val name = properties.string("name")
        val street = properties.string("street")
        val houseNumber = properties.string("housenumber")
        val postcode = properties.string("postcode")
        val city = properties.string("city")
        val district = properties.string("district")
        val county = properties.string("county")
        val state = properties.string("state")
        val country = properties.string("country")

        val streetLine = joinNonBlank(street, houseNumber)
        val localityLine = joinNonBlank(
            postcode?.let { code ->
                city?.let { place -> "$code $place" } ?: code
            } ?: city,
            null,
        ) ?: city

        val title = firstNonBlank(
            name,
            streetLine,
            city,
            district,
            county,
            state,
            country,
            fallbackTitle,
        ) ?: return null

        val subtitleParts = buildList {
            if (!name.isNullOrBlank() && !streetLine.isNullOrBlank() && title != streetLine) {
                add(streetLine)
            }
            if (!localityLine.isNullOrBlank() && title != localityLine) {
                add(localityLine)
            } else if (!district.isNullOrBlank() && title != district && district != city) {
                add(district)
            }
            if (!country.isNullOrBlank() && title != country && !isDenmark(country) && country !in this) {
                add(country)
            }
        }

        val subtitle = subtitleParts.take(2).joinToString(", ").ifBlank { null }
        val categoryHint = formatCategoryHint(firstNonBlank(osmValue, type, osmKey))
        val id = firstNonBlank(
            buildPhotonId(properties),
            "${latitude},${longitude}",
        ) ?: return null

        return SearchResult(
            place = PlaceSummary(
                id = id,
                title = title,
                subtitle = subtitle,
                latitude = latitude,
                longitude = longitude,
                kind = kind,
                categoryHint = categoryHint,
            ),
        )
    }

    private fun buildPhotonId(properties: JsonObject): String? {
        val osmType = properties.string("osm_type")
        val osmId = properties.string("osm_id")
        val osmKey = properties.string("osm_key")
        val osmValue = properties.string("osm_value")
        if (osmType.isNullOrBlank() || osmId.isNullOrBlank()) return null
        return listOfNotNull(osmType, osmId, osmKey, osmValue).joinToString(":")
    }

    private fun formatCategoryHint(raw: String?): String? {
        val value = raw?.trim()?.replace('_', ' ')?.ifBlank { return null } ?: return null
        val locale = Locale("da", "DK")
        val normalized = value.lowercase(locale)
        return normalized.replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase(locale)
            } else {
                char.toString()
            }
        }
    }

    private fun JsonObject.string(key: String): String? {
        return this[key].stringOrNull()
    }

    private fun JsonElement?.objectOrNull(): JsonObject? {
        return this as? JsonObject
    }

    private fun JsonElement?.arrayOrNull(): JsonArray? {
        return this as? JsonArray
    }

    private fun JsonElement?.stringOrNull(): String? {
        return (this as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.trim()
            ?.ifBlank { null }
    }

    private fun JsonElement?.doubleOrNull(): Double? {
        return (this as? JsonPrimitive)?.doubleOrNull
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }

    private fun joinNonBlank(
        first: String?,
        second: String?,
    ): String? {
        return listOfNotNull(first?.takeIf { it.isNotBlank() }, second?.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { null }
    }

    private fun isDenmark(country: String): Boolean {
        return country.equals("Danmark", ignoreCase = true) || country.equals("Denmark", ignoreCase = true)
    }
}
