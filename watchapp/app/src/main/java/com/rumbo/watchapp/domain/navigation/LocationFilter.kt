package com.rumbo.watchapp.domain.navigation

import com.rumbo.watchapp.domain.geo.bearingDeg
import com.rumbo.watchapp.domain.geo.haversineM
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix

// Por debajo de este desplazamiento el rumbo calculado es solo ruido.
private const val MIN_MOVE_FOR_BEARING_M = 1.0

/**
 * Descarta lecturas imprecisas o viejas y suaviza la posición con la mediana de las últimas
 * lecturas buenas. Si el proveedor no da velocidad o rumbo, los calcula entre las posiciones ya
 * suavizadas, así un salto suelto del GPS no los altera. Una velocidad imposible a pie (más de
 * [NavigationThresholds.maxSpeedMps]) se descarta: adelantaría cientos de metros el aviso de giro.
 */
class LocationFilter(private val thresholds: NavigationThresholds = NavigationThresholds()) {

    private val recent = ArrayDeque<LocationFix>()
    private var previous: LocationFix? = null
    private var previousSmoothed: LocationFix? = null

    /** Devuelve la lectura suavizada, o null si se descarta. */
    fun accept(fix: LocationFix, nowMs: Long): LocationFix? {
        if (fix.accuracyM > thresholds.maxAccuracyM) return null
        if (nowMs - fix.timeMs > thresholds.maxFixAgeMs) return null
        val prev = previous
        if (prev != null && fix.timeMs < prev.timeMs) return null

        previous = fix
        recent.addLast(fix)
        if (recent.size > thresholds.medianWindow) recent.removeFirst()

        val smoothed = fix.copy(lat = median(recent.map { it.lat }), lng = median(recent.map { it.lng }))
        val last = previousSmoothed
        val speed = fix.speedMps ?: last?.let { speedBetween(it, smoothed) }
        return smoothed.copy(
            speedMps = speed?.takeIf { it <= thresholds.maxSpeedMps },
            bearingDeg = fix.bearingDeg ?: last?.let { bearingBetween(it, smoothed) },
        ).also { previousSmoothed = it }
    }

    private fun speedBetween(a: LocationFix, b: LocationFix): Double? {
        val seconds = (b.timeMs - a.timeMs) / 1000.0
        if (seconds <= 0) return null
        return haversineM(a.point(), b.point()) / seconds
    }

    private fun bearingBetween(a: LocationFix, b: LocationFix): Double? {
        if (haversineM(a.point(), b.point()) < MIN_MOVE_FOR_BEARING_M) return null
        return bearingDeg(a.point(), b.point())
    }

    private fun LocationFix.point() = Coordinates(lat, lng)
}

internal fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}
