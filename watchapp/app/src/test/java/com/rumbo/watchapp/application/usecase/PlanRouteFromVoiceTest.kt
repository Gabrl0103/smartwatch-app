package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.VoiceResult
import com.rumbo.watchapp.domain.port.PlacesRepository
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.VoiceRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlanRouteFromVoiceTest {

    private val audio = File("orden.aac")
    private val park = Place("Parque Bolívar", 1.2056, -77.2703)
    private val route = Route("Parque Bolívar", 1649.0, 1556.0, emptyList(), emptyList())

    private class FakeVoice(private val result: VoiceResult) : VoiceRepository {
        override suspend fun interpret(audioFile: File) = result
    }

    private class FakePlaces(private val place: Place?) : PlacesRepository {
        var query: String? = null
        var near: Coordinates? = null

        override suspend fun search(query: String, near: Coordinates): Place? {
            this.query = query
            this.near = near
            return place
        }
    }

    private class FakeRoutes(private val route: Route) : RoutesRepository {
        var origin: Coordinates? = null
        var destination: Place? = null

        override suspend fun create(origin: Coordinates, destination: Place): Route {
            this.origin = origin
            this.destination = destination
            return route
        }
    }

    private fun navigate(destination: String?) =
        VoiceResult("llévame al parque Bolívar", "navigate", destination, null)

    @Test
    fun returnsRouteToSpokenDestinationFromFixedOrigin() = runBlocking {
        val places = FakePlaces(park)
        val routes = FakeRoutes(route)
        val useCase = PlanRouteFromVoice(FakeVoice(navigate(" parque Bolívar ")), places, routes)

        val result = useCase(audio)

        assertEquals(route, result)
        assertEquals("parque Bolívar", places.query)
        assertEquals(ORIGIN, places.near)
        assertEquals(ORIGIN, routes.origin)
        assertEquals(park, routes.destination)
    }

    @Test
    fun intentOtherThanNavigateFails() {
        val places = FakePlaces(park)
        val question = VoiceResult("¿qué hora es?", "ask", null, "qué hora es")
        val useCase = PlanRouteFromVoice(FakeVoice(question), places, FakeRoutes(route))

        val error = assertThrows(AppError::class.java) { runBlocking { useCase(audio) } }

        assertEquals(NOT_UNDERSTOOD_MESSAGE, error.message)
        assertNull(places.query)
    }

    @Test
    fun navigateWithoutDestinationFails() {
        val useCase = PlanRouteFromVoice(FakeVoice(navigate("  ")), FakePlaces(park), FakeRoutes(route))

        val error = assertThrows(AppError::class.java) { runBlocking { useCase(audio) } }

        assertEquals(NOT_UNDERSTOOD_MESSAGE, error.message)
    }

    @Test
    fun placeNotFoundFailsWithoutAskingForRoute() {
        val routes = FakeRoutes(route)
        val useCase = PlanRouteFromVoice(FakeVoice(navigate("lugar inventado")), FakePlaces(null), routes)

        val error = assertThrows(AppError::class.java) { runBlocking { useCase(audio) } }

        assertEquals("No encontré «lugar inventado», intenta con otro nombre", error.message)
        assertNull(routes.origin)
    }
}
