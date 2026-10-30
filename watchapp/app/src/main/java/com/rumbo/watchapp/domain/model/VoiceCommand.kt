package com.rumbo.watchapp.domain.model

/** Lo que la persona pidió por voz, ya clasificado a partir de lo que entendió el backend. */
sealed interface VoiceCommand {
    /** "Llévame al Parque Bolívar". */
    data class Navigate(val destination: String) : VoiceCommand

    /** Una pregunta para el asistente: "¿cuánto falta?". */
    data class Ask(val question: String) : VoiceCommand

    /** "Repite": volver a decir la indicación actual. */
    data object Repeat : VoiceCommand

    /** "Cancela" o "termina la ruta". */
    data object Cancel : VoiceCommand

    /** No se entendió nada útil. */
    data object NotUnderstood : VoiceCommand
}

/**
 * Clasifica la respuesta de `POST /voice/interpret`. Un destino o una pregunta vacíos no sirven:
 * el backend devuelve `ask` con texto vacío cuando no entendió nada.
 */
fun VoiceResult.toCommand(): VoiceCommand = when (intent) {
    "navigate" -> destination?.trim()?.takeIf { it.isNotEmpty() }?.let { VoiceCommand.Navigate(it) }
    "ask" -> (question ?: text).trim().takeIf { it.isNotEmpty() }?.let { VoiceCommand.Ask(it) }
    "repeat" -> VoiceCommand.Repeat
    "cancel" -> VoiceCommand.Cancel
    else -> null
} ?: VoiceCommand.NotUnderstood
