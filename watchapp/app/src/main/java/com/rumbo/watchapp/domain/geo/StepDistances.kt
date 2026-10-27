package com.rumbo.watchapp.domain.geo

import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.RouteStep
import kotlin.math.max

/**
 * Distancia a lo largo de la ruta hasta cada paso. Cada paso se busca desde el anterior en
 * adelante, así el resultado nunca disminuye aunque la ruta pase dos veces cerca del mismo punto.
 */
fun stepAlongDistances(
    steps: List<RouteStep>,
    polyline: List<Coordinates>,
    cumulative: List<Double> = cumulativeDistances(polyline),
): List<Double> {
    var minAlong = 0.0
    return steps.map { step ->
        val snap = snapToPolyline(Coordinates(step.lat, step.lng), polyline, cumulative, fromAlongM = minAlong)
        minAlong = max(minAlong, snap.alongM)
        minAlong
    }
}
