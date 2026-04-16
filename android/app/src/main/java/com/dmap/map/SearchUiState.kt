package com.dmap.map

import com.dmap.place.SelectedPlace
import com.dmap.place.SearchResult
import org.maplibre.geojson.FeatureCollection

enum class SearchStatus {
    Idle,
    Loading,
    Results,
    Empty,
    Error,
}

data class SearchUiState(
    val query: String = "",
    val status: SearchStatus = SearchStatus.Idle,
    val results: List<SearchResult> = emptyList(),
    val selectedPlace: SelectedPlace? = null,
    val selectedAreaOutline: FeatureCollection? = null,
    val isEnrichingPlace: Boolean = false,
    val errorMessage: String? = null,
)
