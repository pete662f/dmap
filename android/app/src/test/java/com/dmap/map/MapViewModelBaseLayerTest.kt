package com.dmap.map

import com.dmap.services.search.SearchBias
import com.dmap.services.search.SearchService
import com.dmap.place.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelBaseLayerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `base layer defaults to vector and exposes imagery tile url`() {
        val viewModel = createViewModel()

        assertEquals(MapBaseLayer.Vector, viewModel.uiState.value.mapBaseLayer)
        assertEquals(
            "https://example.com/ortofoto/tiles/{z}/{x}/{y}.jpg",
            viewModel.uiState.value.imageryTileUrl,
        )
    }

    @Test
    fun `toggleBaseLayer alternates between vector and ortofoto`() {
        val viewModel = createViewModel()

        viewModel.toggleBaseLayer()
        assertEquals(MapBaseLayer.Ortofoto, viewModel.uiState.value.mapBaseLayer)

        viewModel.toggleBaseLayer()
        assertEquals(MapBaseLayer.Vector, viewModel.uiState.value.mapBaseLayer)
    }

    @Test
    fun `setBaseLayer is idempotent`() {
        val viewModel = createViewModel()

        viewModel.setBaseLayer(MapBaseLayer.Ortofoto)
        viewModel.setBaseLayer(MapBaseLayer.Ortofoto)

        assertEquals(MapBaseLayer.Ortofoto, viewModel.uiState.value.mapBaseLayer)
    }

    @Test
    fun `imagery tile url can be absent`() {
        val viewModel = createViewModel(imageryTileUrl = null)

        assertNull(viewModel.uiState.value.imageryTileUrl)
    }

    @Test
    fun `imagery layer failure returns to vector mode and shows message`() {
        val viewModel = createViewModel()

        viewModel.setBaseLayer(MapBaseLayer.Ortofoto)
        viewModel.onImageryLayerFailed("Raster source failed")

        assertEquals(MapBaseLayer.Vector, viewModel.uiState.value.mapBaseLayer)
        assertEquals("Raster source failed", viewModel.uiState.value.overlayMessage?.text)
    }

    private fun createViewModel(
        imageryTileUrl: String? = "https://example.com/ortofoto/tiles/{z}/{x}/{y}.jpg",
    ): MapViewModel {
        return MapViewModel(
            searchService = FakeSearchService,
            styleUrl = "https://example.com/style.json",
            backendUrl = "https://example.com",
            imageryTileUrl = imageryTileUrl,
        )
    }

    private object FakeSearchService : SearchService {
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
