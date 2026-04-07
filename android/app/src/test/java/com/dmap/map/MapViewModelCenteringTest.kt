package com.dmap.map

import com.dmap.location.LocateMeResult
import com.dmap.place.PlaceKind
import com.dmap.place.PlaceSummary
import com.dmap.place.SearchResult
import com.dmap.services.search.SearchBias
import com.dmap.services.search.SearchService
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelCenteringTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `camera at user location becomes centered`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = createViewModel()

        viewModel.onCameraIdle(
            latitude = 55.6761,
            longitude = 12.5683,
            zoom = 15.0,
        )
        viewModel.onUserLocationSample(
            latitude = 55.6761,
            longitude = 12.5683,
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.isCenteredOnUser)
    }

    @Test
    fun `selecting search result no longer mutates centered state directly`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = createViewModel()
        centerOnUser(viewModel)

        viewModel.selectSearchResult(
            SearchResult(
                place = PlaceSummary(
                    id = "search:1",
                    title = "Aarhus Cathedral",
                    subtitle = "Store Torv 1, 8000 Aarhus C",
                    latitude = 56.1567,
                    longitude = 10.2108,
                    kind = PlaceKind.Poi,
                    categoryHint = "Cathedral",
                ),
            ),
        )

        assertTrue(viewModel.uiState.value.isCenteredOnUser)

        viewModel.onCameraIdle(
            latitude = 56.1567,
            longitude = 10.2108,
            zoom = 16.0,
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.isCenteredOnUser)
    }

    @Test
    fun `user location drift can unset centered state without camera movement`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = createViewModel()
        centerOnUser(viewModel)

        viewModel.onUserLocationSample(
            latitude = 55.6761,
            longitude = 12.5683 + longitudeOffsetDegrees(
                meters = 18.0,
                atLatitude = 55.6761,
            ),
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.isCenteredOnUser)
    }

    @Test
    fun `user location unavailable clears centered state`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = createViewModel()
        centerOnUser(viewModel)

        viewModel.onUserLocationUnavailable()

        assertFalse(viewModel.uiState.value.isCenteredOnUser)
    }

    @Test
    fun `locate me centered result does not force centered state`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = createViewModel()

        viewModel.onLocateMeResult(LocateMeResult.Centered)
        runCurrent()

        assertFalse(viewModel.uiState.value.isCenteredOnUser)
    }

    private fun centerOnUser(viewModel: MapViewModel) {
        viewModel.onCameraIdle(
            latitude = 55.6761,
            longitude = 12.5683,
            zoom = 15.0,
        )
        viewModel.onUserLocationSample(
            latitude = 55.6761,
            longitude = 12.5683,
        )
    }

    private fun createViewModel(): MapViewModel {
        return MapViewModel(
            searchService = FakeSearchService(),
            styleUrl = "https://example.com/style.json",
            backendUrl = "https://example.com",
        )
    }

    private fun longitudeOffsetDegrees(
        meters: Double,
        atLatitude: Double,
    ): Double {
        val metersPerDegreeLongitude = 111_320.0 * cos(atLatitude * PI / 180.0)
        return meters / metersPerDegreeLongitude
    }

    private class FakeSearchService : SearchService {
        override suspend fun search(
            query: String,
            bias: SearchBias?,
            limit: Int,
        ): List<SearchResult> = emptyList()

        override suspend fun reverseGeocode(
            longitude: Double,
            latitude: Double,
        ): SearchResult? = null
    }
}
