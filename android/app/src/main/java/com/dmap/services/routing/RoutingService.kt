package com.dmap.services.routing

import com.dmap.routing.RouteRequest
import com.dmap.routing.RouteResult

interface RoutingService {
    suspend fun route(request: RouteRequest): RouteResult
}
