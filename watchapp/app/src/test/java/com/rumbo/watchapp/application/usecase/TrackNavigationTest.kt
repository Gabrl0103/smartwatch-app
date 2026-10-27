package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.geo.haversineM
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.VibrationController
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.testing.at
import com.rumbo.watchapp.testing.fixAt
import com.rumbo.watchapp.testing.lRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackNavigationTest {

    private class FakeVibration : VibrationController {
        val played = mutableListOf<VibrationPattern>()
        var stops = 0

        override fun play(pattern: VibrationPattern) {
            played += pattern
        }

        override fun stop() {
            stops++
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

    private val detour = Route(
        destinationName = "Parque de prueba",
        distanceM = 50.0,
        durationS = 40.0,
        steps = listOf(
            at(40.0, 100.0).let { RouteStep(1, Maneuver.STRAIGHT, "Vuelva hacia el oeste.", 50.0, it.lat, it.lng) },
            at(200.0, 200.0).let { RouteStep(2, Maneuver.ARRIVE, "Ha llegado.", 0.0, it.lat, it.lng) },
        ),
        polyline = listOf(at(40.0, 100.0), at(200.0, 200.0)),
    )

    @Test
    fun startVibratesOnRouteAndPublishesNextStep() = runBlocking {
        val vibration = FakeVibration()
        val tracking = TrackNavigation(MutableSharedFlow(), FakeRoutes(detour), vibration, clock = { 0L })

        tracking.start(lRoute(), this)
        try {
            val state = tracking.state.value
            assertEquals(listOf(VibrationPatterns.ON_ROUTE), vibration.played)
            assertEquals(NavigationStatus.ON_ROUTE, state.status)
            assertEquals("Gire a la derecha.", state.nextInstruction)
            assertEquals(VibrationPatterns.ON_ROUTE, state.pattern)
            assertEquals(400.0, state.remainingM, 0.5)
        } finally {
            tracking.stop()
        }
        assertEquals(1, vibration.stops)
    }

    @Test
    fun offRouteVibratesAndRecalculatesFromCurrentPosition() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)
        val vibration = FakeVibration()
        val routes = FakeRoutes(detour)
        var now = 0L
        val tracking = TrackNavigation(fixes, routes, vibration, clock = { now }, tickMs = 60_000)

        tracking.start(lRoute(), this)
        try {
            delay(20)
            listOf(0L, 1_000L, 2_000L).forEach { t ->
                now = t
                fixes.emit(fixAt(40.0, 100.0, t))
                delay(20)
            }
            assertEquals(NavigationStatus.OFF_ROUTE, tracking.state.value.status)
            assertEquals(VibrationPatterns.OFF_ROUTE, vibration.played.last())

            now = 12_000
            fixes.emit(fixAt(40.0, 100.0, now))
            delay(50)

            assertEquals(0.0, haversineM(routes.origin!!, at(40.0, 100.0)), 0.1)
            assertEquals("Parque de prueba", routes.destination?.name)
            assertEquals("Ha llegado.", tracking.state.value.nextInstruction)
            assertTrue(!tracking.state.value.recalculating)
        } finally {
            tracking.stop()
        }
    }
}
