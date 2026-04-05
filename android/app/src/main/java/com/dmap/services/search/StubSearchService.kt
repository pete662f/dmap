package com.dmap.services.search

class StubSearchService : SearchService {
    override suspend fun search(query: String): List<String> = emptyList()
}
