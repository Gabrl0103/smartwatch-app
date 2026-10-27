package com.rumbo.watchapp.domain.navigation

import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix

/** Centro de Pasto: origen de la ruta cuando no hay una ubicación GPS reciente y precisa. */
val ORIGIN = Coordinates(lat = 1.2136, lng = -77.2811)

/** Usa la última lectura si es reciente y precisa; si no, [ORIGIN]. */
fun originFrom(
    lastFix: LocationFix?,
    nowMs: Long,
    thresholds: NavigationThresholds = NavigationThresholds(),
): Coordinates {
    if (lastFix == null) return ORIGIN
    val fresh = nowMs - lastFix.timeMs < thresholds.originMaxAgeMs
    val precise = lastFix.accuracyM < thresholds.originMaxAccuracyM
    return if (fresh && precise) Coordinates(lastFix.lat, lastFix.lng) else ORIGIN
}
