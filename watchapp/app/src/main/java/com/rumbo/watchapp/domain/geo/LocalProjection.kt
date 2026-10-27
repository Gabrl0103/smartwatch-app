package com.rumbo.watchapp.domain.geo

import com.rumbo.watchapp.domain.model.Coordinates
import kotlin.math.cos

/** Punto en un plano local, en metros: x hacia el este, y hacia el norte. */
data class Point2(val x: Double, val y: Double)

/**
 * Proyección equirectangular alrededor de [origin]. A escala de una caminata (pocos km)
 * el error es despreciable y permite usar geometría plana.
 */
class LocalProjection(private val origin: Coordinates) {

    private val cosLat = cos(Math.toRadians(origin.lat))

    fun toXY(point: Coordinates) = Point2(
        x = Math.toRadians(point.lng - origin.lng) * EARTH_RADIUS_M * cosLat,
        y = Math.toRadians(point.lat - origin.lat) * EARTH_RADIUS_M,
    )

    fun toCoordinates(point: Point2) = Coordinates(
        lat = origin.lat + Math.toDegrees(point.y / EARTH_RADIUS_M),
        lng = origin.lng + Math.toDegrees(point.x / (EARTH_RADIUS_M * cosLat)),
    )
}
