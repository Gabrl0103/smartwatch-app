package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.navigation.ORIGIN
import com.rumbo.watchapp.domain.port.PlacesRepository
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.VoiceRepository
import java.io.File

private const val NAVIGATE_INTENT = "navigate"
const val NOT_UNDERSTOOD_MESSAGE = "No entendí el destino, intenta de nuevo"

/**
 * Convierte una orden de voz ("llévame al parque") en una ruta a pie lista para confirmar.
 * [origin] da la ubicación actual (GPS reciente o, si no hay, el centro de Pasto).
 */
class PlanRouteFromVoice(
    private val voice: VoiceRepository,
    private val places: PlacesRepository,
    private val routes: RoutesRepository,
    private val origin: () -> Coordinates = { ORIGIN },
) {
    suspend operator fun invoke(audioFile: File): Route {
        val result = voice.interpret(audioFile)
        val destination = result.destination?.trim().orEmpty()
        if (result.intent != NAVIGATE_INTENT || destination.isEmpty()) {
            throw AppError(NOT_UNDERSTOOD_MESSAGE)
        }
        val from = origin()
        val place = places.search(destination, from)
            ?: throw AppError("No encontré «$destination», intenta con otro nombre")
        return routes.create(from, place)
    }
}
