package com.rumbo.watchapp.domain.geo

import com.rumbo.watchapp.domain.model.Coordinates

/** Distancia acumulada desde el inicio hasta cada punto de la polilínea (la primera es 0). */
fun cumulativeDistances(polyline: List<Coordinates>): List<Double> {
    var total = 0.0
    return polyline.mapIndexed { i, point ->
        if (i > 0) total += haversineM(polyline[i - 1], point)
        total
    }
}
