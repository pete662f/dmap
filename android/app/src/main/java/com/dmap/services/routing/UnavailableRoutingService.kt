package com.dmap.services.routing

import com.dmap.routing.RouteRequest
import com.dmap.routing.RouteResult

class UnavailableRoutingService : RoutingService {
    override suspend fun route(request: RouteRequest): RouteResult {
        return RouteResult.Error("Routing backend is unavailable.")
    }
}
