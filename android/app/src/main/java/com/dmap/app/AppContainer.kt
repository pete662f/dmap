package com.dmap.app

import android.content.Context
import com.dmap.config.BackendUrlProvider
import com.dmap.config.MapBackendConfig
import com.dmap.location.LocationController
import com.dmap.map.MapStyleLoader
import com.dmap.services.routing.RoutingService
import com.dmap.services.routing.StubRoutingService
import com.dmap.services.search.SearchService
import com.dmap.services.search.StubSearchService

class AppContainer(context: Context) {
    val backendConfig: MapBackendConfig = BackendUrlProvider.fromBuildConfig()
    val searchService: SearchService = StubSearchService()
    val routingService: RoutingService = StubRoutingService()
    val locationController: LocationController = LocationController(context.applicationContext)
    val mapStyleLoader: MapStyleLoader = MapStyleLoader(backendConfig)
}
