package com.dmap.services.routing

import com.dmap.routing.RoutePath
import com.dmap.routing.RouteResult
import com.dmap.routing.RouteSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ValhallaResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseRoute(payload: String): RouteResult {
        return try {
            val root = json.parseToJsonElement(payload).jsonObject
            val trip = root["trip"]?.jsonObject
                ?: return RouteResult.Error("Routing backend returned an invalid route response.")

            val summary = trip["summary"]?.jsonObject
                ?: return RouteResult.Error("Routing backend returned a route without a summary.")

            val legs = trip["legs"]?.jsonArray
                ?: return RouteResult.Error("Routing backend returned a route without geometry.")

            val distanceKilometers = summary.double("length")
                ?: return RouteResult.Error("Routing backend did not include route distance.")
            val durationSeconds = summary.double("time")
                ?: return RouteResult.Error("Routing backend did not include route duration.")

            val shapes = legs.mapNotNull { leg ->
                leg.jsonObject["shape"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            }

            if (shapes.isEmpty()) {
                return RouteResult.Error("Routing backend returned a route without geometry.")
            }

            val coordinates = shapes.flatMap { Polyline6.decode(it) }
            if (coordinates.size < 2) {
                return RouteResult.Error("Routing backend returned unusable route geometry.")
            }

            RouteResult.Success(
                summary = RouteSummary(
                    distanceKilometers = distanceKilometers,
                    durationSeconds = durationSeconds,
                ),
                path = RoutePath(coordinates),
            )
        } catch (_: Exception) {
            RouteResult.Error("Routing backend returned an unreadable route response.")
        }
    }

    fun parseFailure(payload: String?, statusCode: Int): RouteResult {
        if (payload.isNullOrBlank()) {
            return RouteResult.Error("Routing backend returned HTTP $statusCode.")
        }

        return runCatching {
            val root = json.parseToJsonElement(payload).jsonObject
            val message = root.string("error") ?: root.string("status_message") ?: root.string("message")
            val errorCode = root.int("error_code") ?: root.int("status")
            when {
                errorCode == 442 -> RouteResult.NoRoute
                message?.contains("no path", ignoreCase = true) == true -> RouteResult.NoRoute
                message?.contains("no route", ignoreCase = true) == true -> RouteResult.NoRoute
                message?.contains("no suitable edges", ignoreCase = true) == true -> RouteResult.NoRoute
                else -> RouteResult.Error(message ?: "Routing backend returned HTTP $statusCode.")
            }
        }.getOrElse {
            RouteResult.Error("Routing backend returned HTTP $statusCode.")
        }
    }

    private fun JsonObject.double(key: String): Double? {
        return get(key)?.jsonPrimitive?.doubleOrNull
    }

    private fun JsonObject.int(key: String): Int? {
        return get(key)?.jsonPrimitive?.intOrNull
    }

    private fun JsonObject.string(key: String): String? {
        return get(key)?.jsonPrimitive?.contentOrNull
    }

    private val JsonPrimitive.contentOrNull: String?
        get() = runCatching { content }.getOrNull()
}
