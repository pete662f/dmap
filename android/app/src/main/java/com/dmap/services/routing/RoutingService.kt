package com.dmap.services.routing

interface RoutingService {
    suspend fun route(
        origin: Pair<Double, Double>,
        destination: Pair<Double, Double>,
    ): String?
}
