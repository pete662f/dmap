package com.dmap.domain

import kotlinx.serialization.Serializable

/**
 * Our own normalised taxonomy — deliberately NOT OSM tags and NOT Overture
 * categories. Both of those are inputs that get mapped onto this; keeping our
 * own means an upstream taxonomy change is a pipeline problem, not a client
 * problem, and the icon set has a closed enum to switch on.
 *
 * Extend by adding members. Never renumber or rename: these values reach the
 * vector tiles and the saved-place rows.
 */
@Serializable
enum class Category(val wire: String) {
    UNKNOWN("unknown"),

    // eat / drink
    RESTAURANT("restaurant"),
    CAFE("cafe"),
    BAR("bar"),
    FAST_FOOD("fast_food"),

    // shop
    SUPERMARKET("supermarket"),
    SHOP("shop"),
    BAKERY("bakery"),
    PHARMACY("pharmacy"),

    // move
    TRANSIT_STOP("transit_stop"),
    TRAIN_STATION("train_station"),
    PARKING("parking"),
    FUEL("fuel"),
    EV_CHARGING("ev_charging"),
    BICYCLE_PARKING("bicycle_parking"),

    // civic
    SCHOOL("school"),
    HOSPITAL("hospital"),
    DOCTOR("doctor"),
    LIBRARY("library"),
    POST("post"),
    BANK("bank"),

    // leisure
    PARK("park"),
    BEACH("beach"),
    MUSEUM("museum"),
    ATTRACTION("attraction"),
    SPORTS("sports"),
    HOTEL("hotel"),

    // places
    ADDRESS("address"),
    LOCALITY("locality"),
    ;

    companion object {
        private val byWire = entries.associateBy { it.wire }

        fun fromWire(wire: String?): Category = wire?.let { byWire[it] } ?: UNKNOWN
    }
}
