package com.dmap.services.search

import com.dmap.place.SearchResult
import java.io.IOException

class UnavailableSearchService : SearchService {
    override suspend fun search(
        query: String,
        bias: SearchBias?,
        limit: Int,
    ): List<SearchResult> {
        throw IOException("Search backend is not configured.")
    }

    override suspend fun reverseGeocode(
        longitude: Double,
        latitude: Double,
    ): SearchResult? {
        throw IOException("Search backend is not configured.")
    }
}
