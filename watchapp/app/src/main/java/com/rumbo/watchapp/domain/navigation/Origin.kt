package com.rumbo.watchapp.domain.navigation

import com.rumbo.watchapp.domain.error.TooFarError
import com.rumbo.watchapp.domain.geo.haversineM
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix

/** Más allá de esto no se pide la ruta a pie: el lugar encontrado casi seguro es otro. */
const val MAX_ROUTE_DISTANCE_M = 50_000.0

/** La última lectura si es reciente y precisa; si no, null: con un origen malo no se pide ruta. */
fun originFrom(
    lastFix: LocationFix?,
    nowMs: Long,
    thresholds: NavigationThresholds = NavigationThresholds(),
): Coordinates? {
    if (lastFix == null) return null
    val fresh = nowMs - lastFix.timeMs < thresholds.originMaxAgeMs
    val precise = lastFix.accuracyM < thresholds.originMaxAccuracyM
    return if (fresh && precise) Coordinates(lastFix.lat, lastFix.lng) else null
}

/** Falla con [TooFarError] si [destination] queda a más de [MAX_ROUTE_DISTANCE_M] de [origin]. */
fun requireWalkable(origin: Coordinates, destination: Coordinates) {
    if (haversineM(origin, destination) > MAX_ROUTE_DISTANCE_M) throw TooFarError()
}
