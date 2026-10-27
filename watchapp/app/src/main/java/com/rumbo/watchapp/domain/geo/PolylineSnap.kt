package com.rumbo.watchapp.domain.geo

import com.rumbo.watchapp.domain.model.Coordinates

/** Resultado de pegar un punto a la ruta. [alongM] es la distancia desde el inicio de la ruta. */
data class PolylineSnap(
    val point: Coordinates,
    val segmentIndex: Int,
    val distanceToRouteM: Double,
    val alongM: Double,
)

/**
 * Pega [point] al segmento más cercano de [polyline]. Con [fromAlongM]..[toAlongM] solo mira
 * los segmentos de esa ventana, para no saltar a otra parte de la ruta que pase cerca.
 */
fun snapToPolyline(
    point: Coordinates,
    polyline: List<Coordinates>,
    cumulative: List<Double> = cumulativeDistances(polyline),
    fromAlongM: Double = Double.NEGATIVE_INFINITY,
    toAlongM: Double = Double.POSITIVE_INFINITY,
): PolylineSnap {
    require(polyline.isNotEmpty()) { "La polilínea está vacía" }
    if (polyline.size == 1) {
        return PolylineSnap(polyline[0], 0, haversineM(point, polyline[0]), 0.0)
    }
    val projection = LocalProjection(point)
    val p = projection.toXY(point)
    var best: PolylineSnap? = null
    for (i in 0 until polyline.size - 1) {
        if (cumulative[i + 1] < fromAlongM || cumulative[i] > toAlongM) continue
        val hit = projectOntoSegment(p, projection.toXY(polyline[i]), projection.toXY(polyline[i + 1]))
        if (best == null || hit.distanceM < best.distanceToRouteM) {
            best = PolylineSnap(
                point = projection.toCoordinates(hit.point),
                segmentIndex = i,
                distanceToRouteM = hit.distanceM,
                alongM = cumulative[i] + hit.fraction * (cumulative[i + 1] - cumulative[i]),
            )
        }
    }
    return best ?: snapToPolyline(point, polyline, cumulative)
}
