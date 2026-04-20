package com.dmap.map

import com.dmap.place.PlaceKind
import com.dmap.place.PlaceSummary
import com.dmap.place.SelectedPlaceOrigin
import com.dmap.place.SelectedPlaceType
import com.dmap.place.SearchResult
import com.dmap.services.search.SearchBias
import com.dmap.services.search.SearchService
import java.io.IOException
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelCoordinateSelectionTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `long press immediately creates a coordinate pin`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val reverseResult = CompletableDeferred<SearchResult?>()
        val searchService = FakeSearchService().apply {
            reverseHandler = { _, _ -> reverseResult.await() }
        }
        val viewModel = createViewModel(searchService)

        viewModel.selectCoordinateFromLongPress(
            longitude = 12.5683,
            latitude = 55.6761,
        )

        val selectedPlace = requireSelectedPlace(viewModel)
        assertEquals(SelectedPlaceType.CoordinatePin, selectedPlace.type)
        assertEquals(SelectedPlaceOrigin.LongPress, selectedPlace.origin)
        assertEquals("Dropped pin", selectedPlace.place.title)
        assertEquals(55.6761, selectedPlace.place.latitude, 0.0)
        assertEquals(12.5683, selectedPlace.place.longitude, 0.0)
        assertEquals("Looking up this spot…", viewModel.uiState.value.overlayMessage?.text)

        reverseResult.complete(null)
        advanceUntilIdle()
    }

    @Test
    fun `reverse geocode success within threshold enriches dropped pin`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val pressedLatitude = 55.6761
        val pressedLongitude = 12.5683
        val searchService = FakeSearchService().apply {
            reverseHandler = { _, _ ->
                SearchResult(
                    place = PlaceSummary(
                        id = "N:123",
                        title = "Nyhavn",
                        subtitle = "København K",
                        latitude = pressedLatitude,
                        longitude = pressedLongitude + longitudeOffsetDegrees(
                            meters = 10.0,
                            atLatitude = pressedLatitude,
                        ),
                        kind = PlaceKind.Address,
                        categoryHint = "Harbor",
                    ),
                )
            }
        }
        val viewModel = createViewModel(searchService)

        viewModel.selectCoordinateFromLongPress(
            longitude = pressedLongitude,
            latitude = pressedLatitude,
        )
        advanceUntilIdle()

        val selectedPlace = requireSelectedPlace(viewModel)
        assertEquals(SelectedPlaceType.CoordinatePin, selectedPlace.type)
        assertEquals("coord:$pressedLatitude,$pressedLongitude", selectedPlace.place.id)
        assertEquals("Nyhavn", selectedPlace.place.title)
        assertEquals("København K", selectedPlace.place.subtitle)
        assertEquals(PlaceKind.Address, selectedPlace.place.kind)
        assertEquals("Harbor", selectedPlace.place.categoryHint)
        assertEquals(pressedLatitude, selectedPlace.place.latitude, 0.0)
        assertEquals(pressedLongitude, selectedPlace.place.longitude, 0.0)
        assertEquals(null, viewModel.uiState.value.overlayMessage)
    }

    @Test
    fun `reverse geocode success beyond threshold keeps dropped pin`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val pressedLatitude = 55.66375
        val pressedLongitude = 12.56876
        val searchService = FakeSearchService().apply {
            reverseHandler = { _, _ ->
                SearchResult(
                    place = PlaceSummary(
                        id = "N:456",
                        title = "Islands Brygge 24B",
                        subtitle = "2300 København S",
                        latitude = pressedLatitude,
                        longitude = pressedLongitude + longitudeOffsetDegrees(
                            meters = 40.0,
                            atLatitude = pressedLatitude,
                        ),
                        kind = PlaceKind.Address,
                        categoryHint = "House",
                    ),
                )
            }
        }
        val viewModel = createViewModel(searchService)

        viewModel.selectCoordinateFromLongPress(
            longitude = pressedLongitude,
            latitude = pressedLatitude,
        )
        advanceUntilIdle()

        val selectedPlace = requireSelectedPlace(viewModel)
        assertEquals("Dropped pin", selectedPlace.place.title)
        assertEquals(null, selectedPlace.place.subtitle)
        assertEquals(PlaceKind.Unknown, selectedPlace.place.kind)
        assertEquals(null, selectedPlace.place.categoryHint)
        assertEquals(pressedLatitude, selectedPlace.place.latitude, 0.0)
        assertEquals(pressedLongitude, selectedPlace.place.longitude, 0.0)
        assertEquals(
            "No nearby place found. Showing a dropped pin.",
            viewModel.uiState.value.overlayMessage?.text,
        )
    }

    @Test
    fun `reverse geocode success at exact threshold is accepted`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val pressedLatitude = 55.6761
        val pressedLongitude = 12.5683
        val searchService = FakeSearchService().apply {
            reverseHandler = { _, _ ->
                SearchResult(
                    place = PlaceSummary(
                        id = "N:789",
                        title = "Threshold Place",
                        subtitle = "Accepted at 25 meters",
                        latitude = pressedLatitude,
                        longitude = pressedLongitude + longitudeOffsetDegrees(
                            meters = 25.0,
                            atLatitude = pressedLatitude,
                        ),
                        kind = PlaceKind.Poi,
                        categoryHint = "Poi",
                    ),
                )
            }
        }
        val viewModel = createViewModel(searchService)

        viewModel.selectCoordinateFromLongPress(
            longitude = pressedLongitude,
            latitude = pressedLatitude,
        )
        advanceUntilIdle()

        val selectedPlace = requireSelectedPlace(viewModel)
        assertEquals("Threshold Place", selectedPlace.place.title)
        assertEquals("Accepted at 25 meters", selectedPlace.place.subtitle)
        assertEquals(PlaceKind.Poi, selectedPlace.place.kind)
        assertEquals("Poi", selectedPlace.place.categoryHint)
        assertEquals(pressedLatitude, selectedPlace.place.latitude, 0.0)
        assertEquals(pressedLongitude, selectedPlace.place.longitude, 0.0)
    }

    @Test
    fun `reverse geocode no result keeps dropped pin`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = createViewModel(
            FakeSearchService().apply {
                reverseHandler = { _, _ -> null }
            },
        )

        viewModel.selectCoordinateFromLongPress(
            longitude = 10.2039,
            latitude = 56.1629,
        )
        advanceUntilIdle()

        val selectedPlace = requireSelectedPlace(viewModel)
        assertEquals("Dropped pin", selectedPlace.place.title)
        assertEquals(56.1629, selectedPlace.place.latitude, 0.0)
        assertEquals(10.2039, selectedPlace.place.longitude, 0.0)
        assertEquals(
            "No nearby place found. Showing a dropped pin.",
            viewModel.uiState.value.overlayMessage?.text,
        )
    }

    @Test
    fun `reverse geocode failure keeps dropped pin`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = createViewModel(
            FakeSearchService().apply {
                reverseHandler = { _, _ -> throw IOException("backend offline") }
            },
        )

        viewModel.selectCoordinateFromLongPress(
            longitude = 9.9217,
            latitude = 57.0488,
        )
        advanceUntilIdle()

        val selectedPlace = requireSelectedPlace(viewModel)
        assertEquals("Dropped pin", selectedPlace.place.title)
        assertEquals(57.0488, selectedPlace.place.latitude, 0.0)
        assertEquals(9.9217, selectedPlace.place.longitude, 0.0)
        assertEquals(
            "Search backend is unavailable. Showing a dropped pin.",
            viewModel.uiState.value.overlayMessage?.text,
        )
    }

    @Test
    fun `stale reverse geocode result does not overwrite a newer selection`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val reverseResult = CompletableDeferred<SearchResult?>()
        val searchService = FakeSearchService().apply {
            reverseHandler = { _, _ -> reverseResult.await() }
        }
        val viewModel = createViewModel(searchService)

        viewModel.selectCoordinateFromLongPress(
            longitude = 12.5683,
            latitude = 55.6761,
        )
        runCurrent()

        val newerSelection = SearchResult(
            place = PlaceSummary(
                id = "search:1",
                title = "Aarhus Cathedral",
                subtitle = "Store Torv 1, 8000 Aarhus C",
                latitude = 56.1567,
                longitude = 10.2108,
                kind = PlaceKind.Poi,
                categoryHint = "Cathedral",
            ),
        )
        viewModel.selectSearchResult(newerSelection)

        reverseResult.complete(
            SearchResult(
                place = PlaceSummary(
                    id = "reverse:1",
                    title = "Older reverse result",
                    subtitle = "Should not win",
                    latitude = 55.6800,
                    longitude = 12.5900,
                    kind = PlaceKind.Address,
                    categoryHint = "Address",
                ),
            ),
        )
        advanceUntilIdle()

        val selectedPlace = requireSelectedPlace(viewModel)
        assertEquals(SelectedPlaceType.PlaceResult, selectedPlace.type)
        assertEquals(SelectedPlaceOrigin.Search, selectedPlace.origin)
        assertEquals("Aarhus Cathedral", selectedPlace.place.title)
        assertEquals(56.1567, selectedPlace.place.latitude, 0.0)
        assertEquals(10.2108, selectedPlace.place.longitude, 0.0)
    }

    @Test
    fun `poi tap reverse geocode enriches subtitle while preserving poi title`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val tappedPoi = PlaceSummary(
            id = "poi:1",
            title = "Dantes Plads",
            subtitle = null,
            latitude = 55.6735,
            longitude = 12.5668,
            kind = PlaceKind.Poi,
            categoryHint = "Square",
        )
        val viewModel = createViewModel(
            FakeSearchService().apply {
                reverseHandler = { _, _ ->
                    SearchResult(
                        place = PlaceSummary(
                            id = "reverse:poi:1",
                            title = "Dantes Plads",
                            subtitle = "1556 København V",
                            latitude = tappedPoi.latitude,
                            longitude = tappedPoi.longitude + longitudeOffsetDegrees(
                                meters = 10.0,
                                atLatitude = tappedPoi.latitude,
                            ),
                            kind = PlaceKind.Address,
                            categoryHint = "Square",
                        ),
                    )
                }
            },
        )

        viewModel.selectRenderedPoi(RenderedPoiSelection(tappedPoi))
        advanceUntilIdle()

        val selectedPlace = requireSelectedPlace(viewModel)
        assertEquals(SelectedPlaceOrigin.PoiTap, selectedPlace.origin)
        assertEquals("Dantes Plads", selectedPlace.place.title)
        assertEquals("1556 København V", selectedPlace.place.subtitle)
        assertEquals("Square", selectedPlace.place.categoryHint)
        assertEquals(tappedPoi.latitude, selectedPlace.place.latitude, 0.0)
        assertEquals(tappedPoi.longitude, selectedPlace.place.longitude, 0.0)
    }

    @Test
    fun `poi tap ignores distant reverse geocode result`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val tappedPoi = PlaceSummary(
            id = "poi:2",
            title = "Dantes Plads",
            subtitle = null,
            latitude = 55.6735,
            longitude = 12.5668,
            kind = PlaceKind.Poi,
            categoryHint = "Square",
        )
        val viewModel = createViewModel(
            FakeSearchService().apply {
                reverseHandler = { _, _ ->
                    SearchResult(
                        place = PlaceSummary(
                            id = "reverse:poi:2",
                            title = "Nearby Address",
                            subtitle = "1556 København V",
                            latitude = tappedPoi.latitude,
                            longitude = tappedPoi.longitude + longitudeOffsetDegrees(
                                meters = 60.0,
                                atLatitude = tappedPoi.latitude,
                            ),
                            kind = PlaceKind.Address,
                            categoryHint = "Address",
                        ),
                    )
                }
            },
        )

        viewModel.selectRenderedPoi(RenderedPoiSelection(tappedPoi))
        advanceUntilIdle()

        val selectedPlace = requireSelectedPlace(viewModel)
        assertEquals("Dantes Plads", selectedPlace.place.title)
        assertNull(selectedPlace.place.subtitle)
        assertEquals("Square", selectedPlace.place.categoryHint)
    }

    @Test
    fun `rendered poi stores area highlight`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val tappedPoi = PlaceSummary(
            id = "area:park:Botanisk Have:55.6869:12.5738",
            title = "Botanisk Have",
            subtitle = null,
            latitude = 55.6869,
            longitude = 12.5738,
            kind = PlaceKind.Poi,
            categoryHint = "Park",
        )
        val areaHighlight = sampleAreaHighlight()
        val viewModel = createViewModel(FakeSearchService())

        viewModel.selectRenderedPoi(RenderedPoiSelection(tappedPoi, areaHighlight))
        runCurrent()

        val state = viewModel.uiState.value.searchUiState
        assertEquals(tappedPoi, state.selectedPlace?.place)
        assertSame(areaHighlight, state.selectedAreaHighlight)
    }

    @Test
    fun `search result clears area highlight`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = createViewModel(FakeSearchService())
        viewModel.selectRenderedPoi(RenderedPoiSelection(samplePoi(), sampleAreaHighlight()))
        runCurrent()

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

        assertNull(viewModel.uiState.value.searchUiState.selectedAreaHighlight)
    }

    @Test
    fun `long press clears area highlight`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = createViewModel(FakeSearchService())
        viewModel.selectRenderedPoi(RenderedPoiSelection(samplePoi(), sampleAreaHighlight()))
        runCurrent()

        viewModel.selectCoordinateFromLongPress(
            longitude = 12.5683,
            latitude = 55.6761,
        )

        assertNull(viewModel.uiState.value.searchUiState.selectedAreaHighlight)
    }

    @Test
    fun `clear selection clears area highlight`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = createViewModel(FakeSearchService())
        viewModel.selectRenderedPoi(RenderedPoiSelection(samplePoi(), sampleAreaHighlight()))
        runCurrent()

        viewModel.clearSelectedPlace()

        val state = viewModel.uiState.value.searchUiState
        assertNull(state.selectedPlace)
        assertNull(state.selectedAreaHighlight)
    }

    private fun createViewModel(searchService: SearchService): MapViewModel {
        return MapViewModel(
            searchService = searchService,
            styleUrl = "https://example.com/style.json",
            backendUrl = "https://example.com",
        )
    }

    private fun requireSelectedPlace(viewModel: MapViewModel) = viewModel.uiState.value.searchUiState.selectedPlace
        .also { assertNotNull(it) }!!

    private fun samplePoi(): PlaceSummary {
        return PlaceSummary(
            id = "poi:1",
            title = "Botanisk Have",
            subtitle = null,
            latitude = 55.6869,
            longitude = 12.5738,
            kind = PlaceKind.Poi,
            categoryHint = "Park",
        )
    }

    private fun sampleAreaHighlight(): SelectedAreaHighlight {
        val ring = listOf(
            Point.fromLngLat(12.0, 55.0),
            Point.fromLngLat(12.1, 55.0),
            Point.fromLngLat(12.1, 55.1),
            Point.fromLngLat(12.0, 55.0),
        )
        return SelectedAreaHighlight(
            fillGeometry = FeatureCollection.fromFeatures(
                arrayOf(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))),
            ),
            outlineGeometry = FeatureCollection.fromFeatures(
                arrayOf(Feature.fromGeometry(LineString.fromLngLats(ring))),
            ),
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
        var reverseHandler: suspend (Double, Double) -> SearchResult? = { _, _ -> null }

        override suspend fun search(
            query: String,
            bias: SearchBias?,
            limit: Int,
        ): List<SearchResult> = emptyList()

        override suspend fun reverseGeocode(
            longitude: Double,
            latitude: Double,
        ): SearchResult? {
            return reverseHandler(longitude, latitude)
        }
    }
}
