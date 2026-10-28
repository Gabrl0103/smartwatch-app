package com.rumbo.watchapp.domain.navigation

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.RouteStep

enum class NavigationStatus { ON_ROUTE, TURN_APPROACHING, MANEUVER, OFF_ROUTE, NO_SIGNAL, ARRIVED }

/** Lo que el motor avisa en cada lectura; cada evento sale una sola vez por situación. */
sealed interface NavigationEvent {
    data class NextStepChanged(val step: RouteStep) : NavigationEvent
    data class TurnApproaching(val maneuver: Maneuver) : NavigationEvent
    data class TurnNow(val maneuver: Maneuver) : NavigationEvent
    data object OffRoute : NavigationEvent
    data object BackOnRoute : NavigationEvent
    data object SignalLost : NavigationEvent
    data object SignalRecovered : NavigationEvent
    data object RecalculateRequested : NavigationEvent
    data object Arrived : NavigationEvent
}

/** Foto del estado del motor para mostrar en pantalla. */
data class NavigationSnapshot(
    val status: NavigationStatus,
    val nextStep: RouteStep?,
    val distanceToNextStepM: Double?,
    /** Largo del tramo que termina en el siguiente paso (desde el paso anterior o el inicio). */
    val stepLengthM: Double?,
    val remainingM: Double,
    val progressM: Double,
    val distanceToRouteM: Double?,
)
