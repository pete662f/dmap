package com.dmap.map

import com.dmap.place.PlaceKind
import com.dmap.place.PlaceSummary
import com.dmap.place.SearchResult
import com.dmap.services.search.SearchBias
import com.dmap.services.search.SearchService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelSearchTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `query change clears stale results immediately while debounce is pending`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = MapViewModel(
            searchService = FakeSearchService,
            styleUrl = "https://example.com/style.json",
            backendUrl = "https://example.com",
        )

        viewModel.updateSearchQuery("aar")
        advanceTimeBy(301)
        advanceUntilIdle()

        assertEquals(SearchStatus.Results, viewModel.uiState.value.searchUiState.status)
        assertTrue(viewModel.uiState.value.searchUiState.results.isNotEmpty())

        viewModel.updateSearchQuery("aars")

        assertEquals(SearchStatus.Loading, viewModel.uiState.value.searchUiState.status)
        assertTrue(viewModel.uiState.value.searchUiState.results.isEmpty())
    }

    @Test
    fun `in-flight stale search result is ignored after query changes`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val searchService = DelayedSearchService()
        val viewModel = MapViewModel(
            searchService = searchService,
            styleUrl = "https://example.com/style.json",
            backendUrl = "https://example.com",
        )

        viewModel.updateSearchQuery("aar")
        advanceTimeBy(301)
        runCurrent()

        assertEquals(listOf("aar"), searchService.queries)

        viewModel.updateSearchQuery("aars")
        assertEquals(SearchStatus.Loading, viewModel.uiState.value.searchUiState.status)
        assertTrue(viewModel.uiState.value.searchUiState.results.isEmpty())

        searchService.firstResult.complete(listOf(searchResult("stale:aar")))
        runCurrent()

        assertEquals(SearchStatus.Loading, viewModel.uiState.value.searchUiState.status)
        assertTrue(viewModel.uiState.value.searchUiState.results.isEmpty())
    }

    private object FakeSearchService : SearchService {
        override suspend fun search(
            query: String,
            bias: SearchBias?,
            limit: Int,
        ): List<SearchResult> {
            return listOf(
                searchResult("search:$query"),
            )
        }

        override suspend fun reverseGeocode(
            longitude: Double,
            latitude: Double,
        ): SearchResult? = null
    }

    private class DelayedSearchService : SearchService {
        val queries = mutableListOf<String>()
        val firstResult = CompletableDeferred<List<SearchResult>>()

        override suspend fun search(
            query: String,
            bias: SearchBias?,
            limit: Int,
        ): List<SearchResult> {
            queries += query
            return if (queries.size == 1) {
                firstResult.await()
            } else {
                listOf(searchResult("search:$query"))
            }
        }

        override suspend fun reverseGeocode(
            longitude: Double,
            latitude: Double,
        ): SearchResult? = null
    }

    private companion object {
        fun searchResult(id: String): SearchResult {
            return SearchResult(
                place = PlaceSummary(
                    id = id,
                    title = "Aarhus",
                    subtitle = "8000 Aarhus C",
                    latitude = 56.1629,
                    longitude = 10.2039,
                    kind = PlaceKind.City,
                ),
            )
        }
    }
}
