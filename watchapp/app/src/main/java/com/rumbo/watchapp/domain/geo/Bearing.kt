package com.rumbo.watchapp.domain.geo

import com.rumbo.watchapp.domain.model.Coordinates
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Rumbo inicial de [from] hacia [to] en grados: 0 = norte, 90 = este. */
fun bearingDeg(from: Coordinates, to: Coordinates): Double {
    val lat1 = Math.toRadians(from.lat)
    val lat2 = Math.toRadians(to.lat)
    val dLng = Math.toRadians(to.lng - from.lng)
    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}
