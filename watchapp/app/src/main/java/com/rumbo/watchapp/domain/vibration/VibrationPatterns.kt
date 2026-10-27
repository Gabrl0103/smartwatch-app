package com.rumbo.watchapp.domain.vibration

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.navigation.NavigationEvent

/** Lenguaje de vibraciones de Rumbo: cada situación tiene un patrón distinto al tacto. */
object VibrationPatterns {

    val ON_ROUTE = VibrationPattern(
        label = "1 pulso suave cada 6 s",
        timingsMs = listOf(0, 300),
        amplitudes = listOf(0, 60),
        repeatEveryMs = 6_000,
    )

    val TURN_APPROACHING = VibrationPattern(
        label = "2 pulsos cortos",
        timingsMs = listOf(0, 100, 150, 100),
        amplitudes = listOf(0, 255, 0, 255),
    )

    val LEFT = VibrationPattern(
        label = "Izquierda: 2 pulsos largos",
        timingsMs = listOf(0, 300, 200, 300),
        amplitudes = listOf(0, 150, 0, 150),
    )

    val RIGHT = VibrationPattern(
        label = "Derecha: 1 pulso largo",
        timingsMs = listOf(0, 700),
        amplitudes = listOf(0, 150),
    )

    val OFF_ROUTE = VibrationPattern(
        label = "3 pulsos: fuera de ruta",
        timingsMs = listOf(0, 500, 200, 500, 200, 500),
        amplitudes = listOf(0, 200, 0, 200, 0, 200),
    )

    val NO_SIGNAL = VibrationPattern(
        label = "1 pulso cada 5 s",
        timingsMs = listOf(0, 600),
        amplitudes = listOf(0, 150),
        repeatEveryMs = 5_000,
    )

    val ARRIVED = VibrationPattern(
        label = "5 pulsos crecientes",
        timingsMs = listOf(0, 100, 150, 200, 150, 300, 150, 400, 150, 500),
        amplitudes = listOf(0, 51, 0, 102, 0, 153, 0, 204, 0, 255),
    )

    // Si una lectura dispara varios eventos, vibra solo el más importante.
    private val PRIORITY = listOf(ARRIVED, OFF_ROUTE, LEFT, RIGHT, TURN_APPROACHING, NO_SIGNAL, ON_ROUTE)

    fun forEvent(event: NavigationEvent): VibrationPattern? = when (event) {
        is NavigationEvent.TurnApproaching -> TURN_APPROACHING
        is NavigationEvent.TurnNow -> when (event.maneuver) {
            Maneuver.LEFT -> LEFT
            Maneuver.RIGHT -> RIGHT
            else -> null
        }
        NavigationEvent.OffRoute -> OFF_ROUTE
        NavigationEvent.SignalLost -> NO_SIGNAL
        NavigationEvent.Arrived -> ARRIVED
        NavigationEvent.BackOnRoute, NavigationEvent.SignalRecovered -> ON_ROUTE
        is NavigationEvent.NextStepChanged, NavigationEvent.RecalculateRequested -> null
    }

    fun mostImportant(events: List<NavigationEvent>): VibrationPattern? =
        events.mapNotNull(::forEvent).minByOrNull { PRIORITY.indexOf(it) }
}
