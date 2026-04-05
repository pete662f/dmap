package com.dmap.services.search

import com.dmap.place.SearchResult

interface SearchService {
    suspend fun search(
        query: String,
        bias: SearchBias? = null,
        limit: Int = 8,
    ): List<SearchResult>

    suspend fun reverseGeocode(
        longitude: Double,
        latitude: Double,
    ): SearchResult?
}
