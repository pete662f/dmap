package com.dmap.services.routing

import com.dmap.routing.RouteCoordinate

object Polyline6 {
    fun decode(encoded: String): List<RouteCoordinate> {
        if (encoded.isBlank()) return emptyList()

        val coordinates = mutableListOf<RouteCoordinate>()
        var index = 0
        var latitude = 0
        var longitude = 0

        while (index < encoded.length) {
            val latitudeDelta = decodeValue(encoded, ::advanceIndex, index)
            latitude += latitudeDelta.first
            index = latitudeDelta.second

            val longitudeDelta = decodeValue(encoded, ::advanceIndex, index)
            longitude += longitudeDelta.first
            index = longitudeDelta.second

            coordinates += RouteCoordinate(
                latitude = latitude / 1_000_000.0,
                longitude = longitude / 1_000_000.0,
            )
        }

        return coordinates
    }

    private fun decodeValue(
        encoded: String,
        nextIndex: (Int) -> Int,
        startIndex: Int,
    ): Pair<Int, Int> {
        var index = startIndex
        var shift = 0
        var result = 0

        while (true) {
            require(index < encoded.length) { "Invalid polyline6 geometry." }
            val value = encoded[index].code - 63
            index = nextIndex(index)
            result = result or ((value and 0x1f) shl shift)
            shift += 5
            if (value < 0x20) break
        }

        val delta = if ((result and 1) != 0) {
            (result shr 1).inv()
        } else {
            result shr 1
        }
        return delta to index
    }

    private fun advanceIndex(index: Int): Int = index + 1
}
