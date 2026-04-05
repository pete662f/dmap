package com.dmap.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import com.dmap.routing.RouteUiState
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class RouteOverlayController {
    fun renderRoute(
        style: Style,
        routeUiState: RouteUiState,
    ) {
        ensureBound(style)
        renderPath(style, routeUiState)
        renderEndpoints(style, routeUiState)
    }

    private fun renderPath(
        style: Style,
        routeUiState: RouteUiState,
    ) {
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_LINE_SOURCE_ID) ?: return
        val coordinates = routeUiState.path?.coordinates.orEmpty()
        if (coordinates.size < 2) {
            source.setGeoJson(FeatureCollection.fromFeatures(arrayOf()))
            return
        }

        source.setGeoJson(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    coordinates.map { coordinate ->
                        Point.fromLngLat(coordinate.longitude, coordinate.latitude)
                    },
                ),
            ),
        )
    }

    private fun renderEndpoints(
        style: Style,
        routeUiState: RouteUiState,
    ) {
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_ENDPOINT_SOURCE_ID) ?: return
        val features = buildList {
            routeUiState.origin?.let { endpoint ->
                add(
                    Feature.fromGeometry(
                        Point.fromLngLat(endpoint.place.longitude, endpoint.place.latitude),
                    ).apply {
                        addStringProperty("role", "origin")
                    },
                )
            }
            routeUiState.destination?.let { endpoint ->
                add(
                    Feature.fromGeometry(
                        Point.fromLngLat(endpoint.place.longitude, endpoint.place.latitude),
                    ).apply {
                        addStringProperty("role", "destination")
                    },
                )
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun ensureBound(style: Style) {
        if (style.getSource(ROUTE_LINE_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(
                    ROUTE_LINE_SOURCE_ID,
                    FeatureCollection.fromFeatures(arrayOf()),
                ),
            )
        }
        if (style.getSource(ROUTE_ENDPOINT_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(
                    ROUTE_ENDPOINT_SOURCE_ID,
                    FeatureCollection.fromFeatures(arrayOf()),
                ),
            )
        }

        if (style.getLayer(ROUTE_LINE_CASING_LAYER_ID) == null) {
            val layer = LineLayer(ROUTE_LINE_CASING_LAYER_ID, ROUTE_LINE_SOURCE_ID).withProperties(
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineColor("#F8FAFD"),
                lineOpacity(0.95f),
                lineWidth(9.0f),
            )
            addBelowSelectedPlace(style, layer)
        }

        if (style.getLayer(ROUTE_LINE_LAYER_ID) == null) {
            style.addLayerAbove(
                LineLayer(ROUTE_LINE_LAYER_ID, ROUTE_LINE_SOURCE_ID).withProperties(
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    lineColor("#1D6EF5"),
                    lineOpacity(0.97f),
                    lineWidth(5.5f),
                ),
                ROUTE_LINE_CASING_LAYER_ID,
            )
        }

        if (style.getLayer(ROUTE_ORIGIN_LAYER_ID) == null) {
            style.addLayerAbove(
                CircleLayer(ROUTE_ORIGIN_LAYER_ID, ROUTE_ENDPOINT_SOURCE_ID)
                    .withFilter(eq(get("role"), literal("origin")))
                    .withProperties(
                        circleRadius(6.5f),
                        circleColor("#139A74"),
                        circleStrokeColor("#FFFFFF"),
                        circleStrokeWidth(2.2f),
                    ),
                ROUTE_LINE_LAYER_ID,
            )
        }

        if (style.getLayer(ROUTE_DESTINATION_LAYER_ID) == null) {
            style.addLayerAbove(
                CircleLayer(ROUTE_DESTINATION_LAYER_ID, ROUTE_ENDPOINT_SOURCE_ID)
                    .withFilter(eq(get("role"), literal("destination")))
                    .withProperties(
                        circleRadius(6.5f),
                        circleColor("#C84545"),
                        circleStrokeColor("#FFFFFF"),
                        circleStrokeWidth(2.2f),
                    ),
                ROUTE_ORIGIN_LAYER_ID,
            )
        }
    }

    private fun addBelowSelectedPlace(
        style: Style,
        layer: LineLayer,
    ) {
        if (style.getLayer(SelectedPlaceMarkerController.MARKER_LAYER_ID) != null) {
            style.addLayerBelow(layer, SelectedPlaceMarkerController.MARKER_LAYER_ID)
        } else {
            style.addLayer(layer)
        }
    }

    companion object {
        private const val ROUTE_LINE_SOURCE_ID = "route-line-source"
        private const val ROUTE_ENDPOINT_SOURCE_ID = "route-endpoint-source"
        private const val ROUTE_LINE_CASING_LAYER_ID = "route-line-casing-layer"
        private const val ROUTE_LINE_LAYER_ID = "route-line-layer"
        private const val ROUTE_ORIGIN_LAYER_ID = "route-origin-layer"
        private const val ROUTE_DESTINATION_LAYER_ID = "route-destination-layer"
    }
}
