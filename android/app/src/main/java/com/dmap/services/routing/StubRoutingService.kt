package com.dmap.services.routing

class StubRoutingService : RoutingService {
    override suspend fun route(
        origin: Pair<Double, Double>,
        destination: Pair<Double, Double>,
    ): String? = null
}
