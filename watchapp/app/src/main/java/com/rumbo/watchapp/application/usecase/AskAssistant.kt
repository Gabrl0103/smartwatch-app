package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.AssistantRepository
import kotlin.math.roundToInt

/**
 * Responde una pregunta hecha por voz. Si se está navegando, le pasa al asistente el estado de la
 * ruta ([routeContext]) para que pueda responder "¿cuánto falta?" o "¿qué sigue?". Solo van el
 * destino, la indicación y las distancias: nunca la posición, que es un dato personal.
 */
class AskAssistant(
    private val assistant: AssistantRepository,
    private val currentRoute: () -> TrackingState?,
) {
    suspend operator fun invoke(question: String): String =
        assistant.ask(question, routeContext(currentRoute()))
}

/** El estado de la ruta en frases cortas para el asistente; vacío si no se está navegando. */
fun routeContext(state: TrackingState?): String {
    if (state == null || state.destinationName.isBlank()) return ""
    val destination = state.destinationName
    val remaining = "Faltan ${state.remainingM.roundToInt()} m para llegar."
    val next = state.nextInstruction?.let { instruction ->
        val distance = state.distanceToNextStepM?.let { ", en ${it.roundToInt()} m" }.orEmpty()
        "Siguiente indicación: ${instruction.trim().trimEnd('.')}$distance."
    }
    return when (state.status) {
        NavigationStatus.ARRIVED -> "La persona ya llegó a $destination."
        NavigationStatus.OFF_ROUTE -> listOfNotNull(
            "Navegando a pie hacia $destination.",
            "La persona se salió de la ruta" + if (state.recalculating) "; se está recalculando." else ".",
            remaining,
        ).joinToString(" ")
        NavigationStatus.NO_SIGNAL -> listOfNotNull(
            "Navegando a pie hacia $destination.",
            "No hay señal de GPS en este momento.",
            next,
            remaining,
        ).joinToString(" ")
        else -> listOfNotNull("Navegando a pie hacia $destination.", next, remaining).joinToString(" ")
    }
}
