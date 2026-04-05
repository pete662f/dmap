package com.dmap.app

import android.content.Context
import com.dmap.config.BackendUrlProvider
import com.dmap.config.MapBackendConfig
import com.dmap.location.LocationController
import com.dmap.map.MapStyleLoader
import com.dmap.services.routing.RoutingService
import com.dmap.services.routing.UnavailableRoutingService
import com.dmap.services.routing.ValhallaRoutingService
import com.dmap.services.search.PhotonSearchService
import com.dmap.services.search.SearchService
import com.dmap.services.search.UnavailableSearchService
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    val backendConfig: MapBackendConfig = BackendUrlProvider.fromBuildConfig()
    private val httpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    val searchService: SearchService = backendConfig.searchBaseUrl?.let { searchBaseUrl ->
        PhotonSearchService(
            httpClient = httpClient,
            baseUrl = searchBaseUrl,
        )
    } ?: UnavailableSearchService()
    val routingService: RoutingService = backendConfig.routingBaseUrl?.let { routingBaseUrl ->
        ValhallaRoutingService(
            httpClient = httpClient,
            baseUrl = routingBaseUrl,
        )
    } ?: UnavailableRoutingService()
    val locationController: LocationController = LocationController(context.applicationContext)
    val mapStyleLoader: MapStyleLoader = MapStyleLoader(backendConfig)
}
