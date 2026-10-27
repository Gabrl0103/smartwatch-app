package com.rumbo.watchapp.domain.geo

import com.rumbo.watchapp.domain.model.Coordinates
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Radio medio de la Tierra en metros. */
const val EARTH_RADIUS_M = 6_371_008.8

/** Distancia sobre la superficie terrestre entre dos puntos, en metros. */
fun haversineM(a: Coordinates, b: Coordinates): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(min(1.0, h)))
}
