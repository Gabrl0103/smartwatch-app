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

/** Redondea hacia arriba para no prometer menos tiempo del real. */
fun formatWalkingMinutes(seconds: Double): String {
    val minutes = ceil(seconds / 60).toInt().coerceAtLeast(1)
    return "$minutes min a pie"
}
