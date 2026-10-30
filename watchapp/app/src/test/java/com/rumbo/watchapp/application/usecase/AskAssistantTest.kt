package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.AssistantRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskAssistantTest {

    private class FakeAssistant : AssistantRepository {
        val asked = mutableListOf<Pair<String, String>>()

        override suspend fun ask(question: String, context: String): String {
            asked += question to context
            return "Faltan unos cuatrocientos metros."
        }
    }

    private val onRoute = TrackingState(
        status = NavigationStatus.TURN_APPROACHING,
        destinationName = "Parque Bolívar",
        nextInstruction = "Gire a la izquierda hacia Carrera 14.",
        nextManeuver = Maneuver.LEFT,
        distanceToNextStepM = 28.4,
        remainingM = 466.2,
    )

    @Test
    fun withoutRouteThereIsNoContext() {
        assertEquals("", routeContext(null))
        assertEquals("", routeContext(TrackingState()))
    }

    @Test
    fun onRouteTellsDestinationNextInstructionAndDistances() {
        assertEquals(
            "Navegando a pie hacia Parque Bolívar. Siguiente indicación: Gire a la izquierda hacia " +
                "Carrera 14, en 28 m. Faltan 466 m para llegar.",
            routeContext(onRoute),
        )
    }

    @Test
    fun offRouteNoSignalAndArrivedAreSaid() {
        val off = routeContext(onRoute.copy(status = NavigationStatus.OFF_ROUTE, recalculating = true))
        assertTrue(off, off.contains("se salió de la ruta; se está recalculando"))
        assertTrue(routeContext(onRoute.copy(status = NavigationStatus.NO_SIGNAL)).contains("No hay señal de GPS"))
        assertEquals("La persona ya llegó a Parque Bolívar.", routeContext(onRoute.copy(status = NavigationStatus.ARRIVED)))
    }

    @Test
    fun contextNeverIncludesThePosition() {
        // Solo destino, indicación y distancias: ni coordenadas ni nada que ubique a la persona.
        val context = routeContext(onRoute)
        assertFalse(context, Regex("-?\\d+\\.\\d{3,}").containsMatchIn(context))
    }

    @Test
    fun asksWithTheCurrentRouteContext() = runBlocking {
        val assistant = FakeAssistant()
        val answer = AskAssistant(assistant) { onRoute }("¿Cuánto falta?")

        assertEquals("Faltan unos cuatrocientos metros.", answer)
        assertEquals(listOf("¿Cuánto falta?" to routeContext(onRoute)), assistant.asked)
    }
}
