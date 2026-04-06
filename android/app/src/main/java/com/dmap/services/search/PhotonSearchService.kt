package com.dmap.services.search

import com.dmap.place.SearchResult
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class PhotonSearchService(
    private val httpClient: OkHttpClient,
    baseUrl: String,
) : SearchService {
    private val baseHttpUrl: HttpUrl = baseUrl.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Invalid search backend URL: $baseUrl")

    override suspend fun search(
        query: String,
        bias: SearchBias?,
        limit: Int,
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val url = baseHttpUrl.newBuilder()
            .addPathSegment("api")
            .addQueryParameter("q", query.trim())
            .addQueryParameter("limit", limit.coerceIn(1, 8).toString())
            .applyBias(bias)
            .build()

        execute(url)
    }

    override suspend fun reverseGeocode(
        longitude: Double,
        latitude: Double,
    ): SearchResult? = withContext(Dispatchers.IO) {
        val url = baseHttpUrl.newBuilder()
            .addPathSegment("reverse")
            .addQueryParameter("lon", longitude.toString())
            .addQueryParameter("lat", latitude.toString())
            .addQueryParameter("limit", "1")
            .addQueryParameter("radius", "0.3")
            .build()

        execute(url, fallbackTitle = "Dropped pin").firstOrNull()
    }

    private fun execute(
        url: HttpUrl,
        fallbackTitle: String? = null,
    ): List<SearchResult> {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Search backend returned ${response.code}")
            }

            val body = response.body?.string()
                ?: throw IOException("Search backend returned an empty response.")

            return PhotonResponseParser.parse(
                payload = body,
                fallbackTitle = fallbackTitle,
            )
        }
    }

    private fun HttpUrl.Builder.applyBias(bias: SearchBias?): HttpUrl.Builder {
        if (bias == null) return this

        return addQueryParameter("lat", bias.latitude.toString())
            .addQueryParameter("lon", bias.longitude.toString())
            .addQueryParameter("zoom", bias.zoom.coerceIn(5, 18).toString())
            .addQueryParameter("location_bias_scale", "0.18")
    }
}
