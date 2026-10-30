package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.VoiceCommand
import com.rumbo.watchapp.domain.model.toCommand
import com.rumbo.watchapp.domain.navigation.requireWalkable
import com.rumbo.watchapp.domain.port.PlacesRepository
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.VoiceRepository
import java.io.File

const val NOT_UNDERSTOOD_MESSAGE = "No te entendí, intenta de nuevo"

/**
 * Lo que se dijo al micrófono: [interpret] lo clasifica (ir a un lugar, preguntar, repetir,
 * cancelar) y [routeTo] convierte un destino ("el parque") en una ruta a pie lista para
 * confirmar. [origin] da la ubicación actual: espera al GPS si hace falta y falla si no hay señal.
 */
class PlanRouteFromVoice(
    private val voice: VoiceRepository,
    private val places: PlacesRepository,
    private val routes: RoutesRepository,
    private val origin: suspend () -> Coordinates,
) {
    suspend fun interpret(audioFile: File): VoiceCommand = voice.interpret(audioFile).toCommand()

    suspend fun routeTo(destination: String): Route {
        val from = origin()
        val place = places.search(destination, from)
            ?: throw AppError("No encontré «$destination», intenta con otro nombre")
        requireWalkable(from, Coordinates(place.lat, place.lng))
        return routes.create(from, place)
    }
}
