package com.rumbo.watchapp.domain.navigation

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.testing.fixAt
import com.rumbo.watchapp.testing.lRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationEngineTest {

    private val route = lRoute()
    private val engine = NavigationEngine(route, startMs = 0)
    private var now = 0L

    /** Envía una lectura por segundo en cada punto (este, norte) y junta los eventos. */
    private fun walk(vararg points: Pair<Double, Double>, speedMps: Double = 1.4): List<NavigationEvent> =
        points.flatMap { (east, north) ->
            now += 1_000
            engine.onLocation(fixAt(east, north, now, speedMps = speedMps), now)
        }

    private fun north(from: Int, to: Int, step: Int = 5) =
        (from..to step step).map { 0.0 to it.toDouble() }.toTypedArray()

    private fun east(from: Int, to: Int, step: Int = 5) =
        (from..to step step).map { it.toDouble() to 200.0 }.toTypedArray()

    @Test
    fun followingTheRouteWithLateralNoiseEmitsNoEvents() {
        val noisy = (0..150 step 5).mapIndexed { i, n -> (if (i % 2 == 0) 3.0 else -3.0) to n.toDouble() }

        val events = walk(*noisy.toTypedArray())

        assertEquals(emptyList<NavigationEvent>(), events)
        assertEquals(NavigationStatus.ON_ROUTE, engine.snapshot.status)
        assertEquals("Gire a la derecha.", engine.snapshot.nextStep?.instruction)
        assertEquals(50.0, engine.snapshot.distanceToNextStepM!!, 0.5)
        assertEquals(200.0, engine.snapshot.stepLengthM!!, 0.5)
    }

    @Test
    fun turnApproachingFiresOnceWithinThirtyMeters() {
        val events = walk(*north(100, 185))

        assertEquals(listOf(NavigationEvent.TurnApproaching(Maneuver.RIGHT)), events)
        assertEquals(NavigationStatus.TURN_APPROACHING, engine.snapshot.status)
    }

    @Test
    fun approachDistanceGrowsWithSpeed() {
        val slow = walk(*north(100, 140), speedMps = 5.0)
        val fast = walk(0.0 to 150.0, speedMps = 5.0)

        assertEquals(emptyList<NavigationEvent>(), slow)
        assertEquals(listOf(NavigationEvent.TurnApproaching(Maneuver.RIGHT)), fast)
    }

    @Test
    fun maneuverFiresOnceWithinTwelveMeters() {
        val events = walk(*north(100, 195))

        assertEquals(
            listOf(NavigationEvent.TurnApproaching(Maneuver.RIGHT), NavigationEvent.TurnNow(Maneuver.RIGHT)),
            events,
        )
        assertEquals(NavigationStatus.MANEUVER, engine.snapshot.status)
    }

    @Test
    fun jumpingStraightIntoTheManeuverStillWarnsInTwoStages() {
        walk(*north(100, 140))

        // Un salto del GPS de 60 m a 8 m del giro: nunca hubo lectura en la zona de giro próximo.
        val events = walk(0.0 to 192.0)

        assertEquals(
            listOf(NavigationEvent.TurnApproaching(Maneuver.RIGHT), NavigationEvent.TurnNow(Maneuver.RIGHT)),
            events,
        )
        assertEquals(NavigationStatus.MANEUVER, engine.snapshot.status)
        assertEquals(emptyList<NavigationEvent>(), walk(0.0 to 195.0))
    }

    @Test
    fun passingTheTurnMovesToNextStep() {
        walk(*north(100, 195))

        val events = walk(*east(5, 30))

        assertEquals(listOf(NavigationEvent.NextStepChanged(route.steps[2])), events)
        assertEquals(NavigationStatus.ON_ROUTE, engine.snapshot.status)
        assertEquals(170.0, engine.snapshot.distanceToNextStepM!!, 0.5)
        // El tramo nuevo va del giro (200 m) a la llegada (400 m).
        assertEquals(200.0, engine.snapshot.stepLengthM!!, 0.5)
    }

    @Test
    fun offRouteNeedsThreeReadingsInARow() {
        walk(*north(0, 100, step = 10))

        assertEquals(emptyList<NavigationEvent>(), walk(40.0 to 100.0, 40.0 to 102.0))
        assertEquals(NavigationStatus.ON_ROUTE, engine.snapshot.status)

        assertEquals(listOf(NavigationEvent.OffRoute), walk(40.0 to 104.0))
        assertEquals(NavigationStatus.OFF_ROUTE, engine.snapshot.status)
    }

    @Test
    fun aSingleFarReadingDoesNotCountAsOffRoute() {
        walk(*north(0, 100, step = 10))

        val events = walk(40.0 to 100.0, 40.0 to 102.0, 0.0 to 104.0, 40.0 to 106.0, 40.0 to 108.0)

        assertEquals(emptyList<NavigationEvent>(), events)
    }

    @Test
    fun backOnRouteUsesHysteresis() {
        walk(*north(0, 100, step = 10))
        walk(40.0 to 100.0, 40.0 to 100.0, 40.0 to 100.0)

        assertEquals(emptyList<NavigationEvent>(), walk(20.0 to 100.0))
        assertEquals(NavigationStatus.OFF_ROUTE, engine.snapshot.status)

        assertEquals(listOf(NavigationEvent.BackOnRoute), walk(10.0 to 100.0))
        assertEquals(NavigationStatus.ON_ROUTE, engine.snapshot.status)
    }

    @Test
    fun recalculationIsRequestedAfterTenSecondsOffRouteAndThenEveryTen() {
        walk(*north(0, 100, step = 10))
        walk(40.0 to 100.0, 40.0 to 100.0, 40.0 to 100.0)
        val offSince = now

        val beforeTen = (1..9).flatMap { walk(40.0 to 100.0) }
        assertEquals(emptyList<NavigationEvent>(), beforeTen)
        assertEquals(offSince + 9_000, now)

        assertEquals(listOf(NavigationEvent.RecalculateRequested), walk(40.0 to 100.0))
        assertEquals(emptyList<NavigationEvent>(), (1..9).flatMap { walk(40.0 to 100.0) })
        assertEquals(listOf(NavigationEvent.RecalculateRequested), walk(40.0 to 100.0))
    }

    @Test
    fun signalLostAfterTenSecondsWithoutReadingsAndRecovered() {
        assertEquals(emptyList<NavigationEvent>(), engine.onTick(9_000))
        assertEquals(listOf(NavigationEvent.SignalLost), engine.onTick(10_000))
        assertEquals(NavigationStatus.NO_SIGNAL, engine.snapshot.status)
        assertEquals(emptyList<NavigationEvent>(), engine.onTick(11_000))

        now = 12_000
        val events = engine.onLocation(fixAt(0.0, 5.0, now), now)

        assertEquals(listOf(NavigationEvent.SignalRecovered), events)
        assertEquals(NavigationStatus.ON_ROUTE, engine.snapshot.status)
    }

    @Test
    fun arrivalWithinFifteenMetersEndsNavigation() {
        walk(*north(0, 195, step = 10))
        walk(*north(195, 200))

        val events = walk(*east(5, 190, step = 10))

        assertTrue(events.last() == NavigationEvent.Arrived)
        assertEquals(1, events.count { it == NavigationEvent.Arrived })
        assertEquals(NavigationStatus.ARRIVED, engine.snapshot.status)
        assertEquals(0.0, engine.snapshot.remainingM, 1e-9)
        assertEquals(emptyList<NavigationEvent>(), walk(195.0 to 200.0))
        assertEquals(emptyList<NavigationEvent>(), engine.onTick(now + 60_000))
    }

    @Test
    fun progressNeverGoesBackWithGpsNoise() {
        val jittery = listOf(10.0, 6.0, 15.0, 11.0, 20.0, 14.0, 25.0, 22.0, 30.0, 18.0, 35.0)
        var lastProgress = 0.0
        var lastDistanceToStep = Double.MAX_VALUE

        jittery.forEach { north ->
            walk(0.0 to north)
            val snapshot = engine.snapshot
            assertTrue(snapshot.progressM >= lastProgress)
            assertTrue(snapshot.distanceToNextStepM!! <= lastDistanceToStep)
            lastProgress = snapshot.progressM
            lastDistanceToStep = snapshot.distanceToNextStepM!!
        }
        assertEquals(35.0, lastProgress, 0.5)
    }

    @Test
    fun firstFixHalfwayAlongTheRouteResumesThereWithoutGoingOffRoute() {
        // "Continuar ruta anterior" a mitad de camino: ya dobló y va por el tramo este.
        val events = walk(100.0 to 200.0, 105.0 to 200.0, 110.0 to 200.0)

        assertEquals(listOf(NavigationEvent.NextStepChanged(route.steps[2])), events)
        assertEquals(NavigationStatus.ON_ROUTE, engine.snapshot.status)
        assertEquals(310.0, engine.snapshot.progressM, 0.5)
        assertEquals(90.0, engine.snapshot.remainingM, 0.5)
    }

    @Test
    fun laterFixesStillOnlyLookNearTheCurrentProgress() {
        walk(*north(0, 50))

        // Ya ubicado, una lectura sobre el tramo este (lejos del progreso) no lo hace saltar.
        val events = walk(100.0 to 200.0)

        assertEquals(emptyList<NavigationEvent>(), events)
        assertEquals(50.0, engine.snapshot.progressM, 0.5)
    }
}
