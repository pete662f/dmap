package com.dmap.services.search

interface SearchService {
    suspend fun search(query: String): List<String>
}
