package com.rumbo.watchapp.domain.navigation

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.RouteStep

enum class NavigationStatus { ON_ROUTE, TURN_APPROACHING, MANEUVER, OFF_ROUTE, NO_SIGNAL, ARRIVED }

/** Lo que el motor avisa en cada lectura; cada evento sale una sola vez por situación. */
sealed interface NavigationEvent {
    data class NextStepChanged(val step: RouteStep) : NavigationEvent

    /** Aviso lejano, al empezar un tramo largo. No vibra: solo la voz cuenta qué viene. */
    data class TurnAhead(val maneuver: Maneuver) : NavigationEvent
    data class TurnApproaching(val maneuver: Maneuver) : NavigationEvent

    /**
     * [then] es el giro siguiente si está tan cerca que hay que avisarlo junto con este e
     * [instruction], el texto del paso si es desconocido: un paso pasado por sorpresa ya no está
     * en la foto del motor.
     */
    data class TurnNow(
        val maneuver: Maneuver,
        val roundaboutExit: Int? = null,
        val then: Maneuver? = null,
        val instruction: String = "",
    ) : NavigationEvent
    data object OffRoute : NavigationEvent
    data object BackOnRoute : NavigationEvent
    data object SignalLost : NavigationEvent
    data object SignalRecovered : NavigationEvent
    data object RecalculateRequested : NavigationEvent

    /** El destino está a menos de [NavigationThresholds.destinationNearM]. Solo la voz lo cuenta. */
    data object DestinationNear : NavigationEvent
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
    /** Calle por la que se va ahora, la del último paso ya pasado; vacía si no se conoce. */
    val currentStreet: String = "",
    /** El giro que sigue a [nextStep], si está tan cerca que se avisa junto con él. */
    val thenManeuver: Maneuver? = null,
    /** El paso después de [nextStep] y cuánto hay hasta él: lo que se avisará al pasarlo. */
    val followingStep: RouteStep? = null,
    val followingLegM: Double? = null,
)
