package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.application.speech.DESTINATION_ALREADY_SAVED_PHRASE
import com.rumbo.watchapp.application.speech.DESTINATION_JUST_SAVED_PHRASE
import com.rumbo.watchapp.application.speech.DESTINATION_NOT_SAVED_PHRASE
import com.rumbo.watchapp.application.speech.DESTINATION_SAVED_PHRASE
import com.rumbo.watchapp.application.speech.confirmPhrase
import com.rumbo.watchapp.application.speech.remainingPhrase
import com.rumbo.watchapp.application.speech.routeLengthPhrase
import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.VoiceCommand
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.NavigationRunner

const val NOTHING_TO_REPEAT_MESSAGE = "No hay ninguna ruta en curso para repetir"
const val NOTHING_TO_CANCEL_MESSAGE = "No hay ninguna ruta en curso para cancelar"
const val NOTHING_TO_SAVE_MESSAGE = "No hay ningún destino para guardar"
const val NO_DESTINATION_MESSAGE = "Todavía no hay un destino"

/** En qué terminó una orden de voz: lo que la pantalla tiene que mostrar y decir. */
sealed interface VoiceOutcome {
    /** Hay una ruta nueva para confirmar. */
    data class Confirm(val route: Route) : VoiceOutcome

    /** Respuesta del asistente a [question]. */
    data class Answer(val question: String, val answer: String) : VoiceOutcome

    /** Se cumplió: se dice [text] y se sigue donde se estaba. */
    data class Done(val text: String) : VoiceOutcome

    /** El destino quedó guardado: como [Done], y además hay que mostrarlo. */
    data class Saved(val text: String) : VoiceOutcome

    /** Se repitió la indicación de la ruta, que ya vibra y habla por sí sola. */
    data object Repeated : VoiceOutcome

    /** Terminar la ruta se pregunta: no puede depender de una orden mal entendida. */
    data object AskToFinish : VoiceOutcome

    /** Se descartó el destino que se estaba confirmando. */
    data object DestinationDropped : VoiceOutcome

    /** No se pudo: se dice [text] y se sigue donde se estaba. */
    data class Refused(val text: String) : VoiceOutcome

    /** No se entendió: se ofrece volver a intentarlo. */
    data object NotUnderstood : VoiceOutcome
}

/**
 * Hace lo que se pidió por voz. Lo mismo significa algo distinto según dónde se diga: con una ruta
 * en curso manda ella (no se cambia de destino), en Confirmar se habla de la ruta `confirming`, y
 * en Inicio solo se puede pedir un destino o preguntar. Mostrarlo, decirlo y vibrar el resultado
 * queda a cargo de la pantalla. Un error de red sube como [AppError].
 */
class ExecuteVoiceCommand(
    private val planRoute: PlanRouteFromVoice,
    private val savedPlaces: ManageSavedPlaces,
    private val askAssistant: AskAssistant,
    private val navigation: NavigationRunner,
    private val tracking: () -> TrackingState,
    private val repeatInstruction: () -> Unit,
) {
    suspend operator fun invoke(command: VoiceCommand, confirming: Route? = null): VoiceOutcome {
        val active = navigation.activeRoute
        return when (command) {
            is VoiceCommand.Ask -> VoiceOutcome.Answer(command.question, askAssistant(command.question))
            is VoiceCommand.Navigate -> navigate(command.destination, active)
            VoiceCommand.Repeat -> repeat(active, confirming)
            VoiceCommand.Cancel -> when {
                active != null -> VoiceOutcome.AskToFinish
                confirming != null -> VoiceOutcome.DestinationDropped
                else -> VoiceOutcome.Refused(NOTHING_TO_CANCEL_MESSAGE)
            }
            VoiceCommand.SaveDestination -> save(active, confirming)
            VoiceCommand.HowFar -> howFar(active, confirming)
            VoiceCommand.SavedStatus -> when (val route = active ?: confirming) {
                null -> VoiceOutcome.Refused(NO_DESTINATION_MESSAGE)
                else -> VoiceOutcome.Done(if (route.destinationSaved) DESTINATION_SAVED_PHRASE else DESTINATION_NOT_SAVED_PHRASE)
            }
            VoiceCommand.NotUnderstood -> VoiceOutcome.NotUnderstood
        }
    }

    private suspend fun navigate(destination: String, active: Route?): VoiceOutcome {
        if (active != null) {
            return VoiceOutcome.Refused("Ya vas hacia ${active.destinationName}. Para ir a otro lugar, termina esta ruta.")
        }
        val route = planRoute.routeTo(destination)
        return VoiceOutcome.Confirm(route.copy(destinationSaved = savedPlaces.isSaved(route)))
    }

    private fun repeat(active: Route?, confirming: Route?): VoiceOutcome = when {
        active != null -> {
            repeatInstruction()
            VoiceOutcome.Repeated
        }
        confirming != null -> VoiceOutcome.Done(confirmPhrase(confirming))
        else -> VoiceOutcome.Refused(NOTHING_TO_REPEAT_MESSAGE)
    }

    private suspend fun save(active: Route?, confirming: Route?): VoiceOutcome {
        val route = active ?: confirming ?: return VoiceOutcome.Refused(NOTHING_TO_SAVE_MESSAGE)
        if (route.destinationSaved) return VoiceOutcome.Done(DESTINATION_ALREADY_SAVED_PHRASE)
        try {
            savedPlaces.saveDestination(route)
        } catch (e: AppError) {
            return VoiceOutcome.Refused(SAVE_FAILED_MESSAGE)
        }
        if (active != null) navigation.markDestinationSaved()
        return VoiceOutcome.Saved(DESTINATION_JUST_SAVED_PHRASE)
    }

    private fun howFar(active: Route?, confirming: Route?): VoiceOutcome {
        if (active != null) {
            val state = tracking()
            if (state.status == NavigationStatus.ARRIVED) return VoiceOutcome.Done("Ya llegaste.")
            return VoiceOutcome.Done(remainingPhrase(active, state.remainingM))
        }
        if (confirming != null) return VoiceOutcome.Done(routeLengthPhrase(confirming))
        return VoiceOutcome.Refused(NO_DESTINATION_MESSAGE)
    }
}
