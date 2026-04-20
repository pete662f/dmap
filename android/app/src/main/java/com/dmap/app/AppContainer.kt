package com.dmap.app

import android.content.Context
import com.dmap.config.BackendUrlProvider
import com.dmap.config.MapBackendConfig
import com.dmap.location.LocationController
import com.dmap.map.MapStyleLoader
import com.dmap.services.routing.RoutingService
import com.dmap.services.routing.StubRoutingService
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
    // Routing remains intentionally stubbed for M2; backend URL config is reserved for the routing milestone.
    val routingService: RoutingService = StubRoutingService()
    val locationController: LocationController = LocationController(context.applicationContext)
    val mapStyleLoader: MapStyleLoader = MapStyleLoader(backendConfig)
}
