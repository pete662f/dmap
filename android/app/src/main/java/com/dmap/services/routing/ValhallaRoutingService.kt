package com.dmap.services.routing

import com.dmap.routing.RouteRequest
import com.dmap.routing.RouteResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ValhallaRoutingService(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
) : RoutingService {
    override suspend fun route(request: RouteRequest): RouteResult = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put(
                "locations",
                buildJsonArray {
                    addLocation(request.origin.place.latitude, request.origin.place.longitude)
                    addLocation(request.destination.place.latitude, request.destination.place.longitude)
                },
            )
            put("costing", request.travelMode.valhallaCosting)
            put("units", "kilometers")
            put(
                "directions_options",
                buildJsonObject {
                    put("language", "da-DK")
                },
            )
        }.toString()

        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/route")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                return@withContext ValhallaResponseParser.parseFailure(
                    payload = body,
                    statusCode = response.code,
                )
            }

            if (body.isNullOrBlank()) {
                return@withContext RouteResult.Error("Routing backend returned an empty response.")
            }

            return@withContext ValhallaResponseParser.parseRoute(body)
        }
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addLocation(
        latitude: Double,
        longitude: Double,
    ) {
        add(
            buildJsonObject {
                put("lat", latitude)
                put("lon", longitude)
            },
        )
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
