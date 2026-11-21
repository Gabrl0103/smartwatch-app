package com.rumbo.watchapp.presentation.format

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private val SPANISH = Locale.forLanguageTag("es-CO")

/** 850 → "850 m"; 1649 → "1,6 km". */
fun formatDistance(meters: Double): String =
    if (meters < 1000) {
        "${meters.roundToInt()} m"
    } else {
        String.format(SPANISH, "%.1f km", meters / 1000)
    }

/** Igual que [formatDistance] pero con las unidades en palabras, para TalkBack: "850 metros". */
fun describeDistance(meters: Double): String =
    if (meters < 1000) {
        "${meters.roundToInt()} metros"
    } else {
        String.format(SPANISH, "%.1f kilómetros", meters / 1000)
    }

/** Redondea hacia arriba para no prometer menos tiempo del real. */
fun formatWalkingMinutes(seconds: Double): String {
    val minutes = ceil(seconds / 60).toInt().coerceAtLeast(1)
    return "$minutes min a pie"
}

/**
 * Minutos a pie que faltan, al ritmo de la ruta: [remainingM] por lo que tarda cada metro
 * ([durationS] / [distanceM]), redondeado hacia arriba y nunca menos de 1. Null si la ruta no
 * trae distancia o tiempo.
 */
fun remainingWalkingMinutes(remainingM: Double, distanceM: Double, durationS: Double): Int? {
    if (distanceM <= 0 || durationS <= 0) return null
    return ceil(remainingM.coerceAtLeast(0.0) * durationS / distanceM / 60).toInt().coerceAtLeast(1)
}

/** "24 min": el tiempo corto para mostrar junto a la distancia. */
fun formatMinutes(minutes: Int): String = "$minutes min"
