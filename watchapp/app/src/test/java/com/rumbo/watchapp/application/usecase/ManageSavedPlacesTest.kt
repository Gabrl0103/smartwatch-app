package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.domain.navigation.ORIGIN
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.SavedPlacesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ManageSavedPlacesTest {

    private val home = SavedPlace(1, "Casa", 1.2146, -77.2783)
    private val park = SavedPlace(2, "Parque Bolívar", 1.2056, -77.2703)
    private val route = Route(
        destinationName = "Parque Bolívar",
        distanceM = 1649.0,
        durationS = 1556.0,
        steps = listOf(RouteStep(1, Maneuver.ARRIVE, "Ha llegado.", 0.0, 1.2050, -77.2700)),
        polyline = listOf(Coordinates(1.2136, -77.2811), Coordinates(1.2055, -77.2702)),
        id = 7,
    )

    private class FakeSavedPlaces(initial: List<SavedPlace> = emptyList()) : SavedPlacesRepository {
        val places = initial.toMutableList()
        var error: AppError? = null

        override suspend fun list() = places.toList()

        override suspend fun add(name: String, lat: Double, lng: Double): SavedPlace {
            error?.let { throw it }
            return SavedPlace(places.size + 1L, name, lat, lng).also { places += it }
        }

        override suspend fun delete(id: Long) {
            error?.let { throw it }
            places.removeAll { it.id == id }
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

    @Test
    fun listsSavedPlaces() = runBlocking {
        val useCase = ManageSavedPlaces(FakeSavedPlaces(listOf(home, park)), FakeRoutes(route))

        assertEquals(listOf(home, park), useCase.list())
    }

    @Test
    fun plansRouteFromCurrentLocationToSavedPlace() = runBlocking {
        val here = Coordinates(1.2146, -77.2783)
        val routes = FakeRoutes(route)
        val useCase = ManageSavedPlaces(FakeSavedPlaces(), routes, origin = { here })

        val result = useCase.planRoute(park)

        assertEquals(route, result)
        assertEquals(here, routes.origin)
        assertEquals(Place("Parque Bolívar", 1.2056, -77.2703), routes.destination)
    }

    @Test
    fun plansRouteFromPastoCenterByDefault() = runBlocking {
        val routes = FakeRoutes(route)

        ManageSavedPlaces(FakeSavedPlaces(), routes).planRoute(home)

        assertEquals(ORIGIN, routes.origin)
    }

    @Test
    fun savesDestinationWithItsNameAtTheEndOfTheRoute() = runBlocking {
        val repository = FakeSavedPlaces()
        val useCase = ManageSavedPlaces(repository, FakeRoutes(route))

        val saved = useCase.saveDestination(route)

        assertEquals(SavedPlace(1, "Parque Bolívar", 1.2055, -77.2702), saved)
        assertEquals(listOf(saved), repository.places)
    }

    @Test
    fun savingWithoutPolylineUsesLastStep() = runBlocking {
        val repository = FakeSavedPlaces()
        val useCase = ManageSavedPlaces(repository, FakeRoutes(route))

        val saved = useCase.saveDestination(route.copy(polyline = emptyList()))

        assertEquals(1.2050, saved.lat, 1e-9)
        assertEquals(-77.2700, saved.lng, 1e-9)
    }

    @Test
    fun saveErrorsPropagateWithTheirMessage() {
        val repository = FakeSavedPlaces().apply { error = AppError("No pude conectar con el servidor") }
        val useCase = ManageSavedPlaces(repository, FakeRoutes(route))

        val error = assertThrows(AppError::class.java) { runBlocking { useCase.saveDestination(route) } }

        assertEquals("No pude conectar con el servidor", error.message)
        assertEquals(emptyList<SavedPlace>(), repository.places)
    }

    @Test
    fun deletesPlaceById() = runBlocking {
        val repository = FakeSavedPlaces(listOf(home, park))
        val useCase = ManageSavedPlaces(repository, FakeRoutes(route))

        useCase.delete(home)

        assertEquals(listOf(park), useCase.list())
        assertNull(repository.places.find { it.id == home.id })
    }
}
