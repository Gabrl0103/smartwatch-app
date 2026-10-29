package com.rumbo.watchapp.domain.navigation

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.navigation.NavigationStatus.ARRIVED
import com.rumbo.watchapp.domain.navigation.NavigationStatus.MANEUVER
import com.rumbo.watchapp.domain.navigation.NavigationStatus.NO_SIGNAL
import com.rumbo.watchapp.domain.navigation.NavigationStatus.OFF_ROUTE
import com.rumbo.watchapp.domain.navigation.NavigationStatus.ON_ROUTE
import com.rumbo.watchapp.domain.navigation.NavigationStatus.TURN_APPROACHING
import com.rumbo.watchapp.testing.Replay
import com.rumbo.watchapp.testing.readGpx
import com.rumbo.watchapp.testing.replay
import com.rumbo.watchapp.testing.routeFromGpx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Un estado que aparece y desaparece en menos de esto entre dos iguales es un parpadeo.
private const val MIN_STABLE_MS = 3_000L

private val ON_ROUTE_STATES = setOf(ON_ROUTE, TURN_APPROACHING, MANEUVER)

/**
 * La máquina de estados con los tres recorridos de docs/demo (centro de Pasto → Parque Bolívar,
 * 1 970 m), pasando cada punto por el mismo filtro que en el reloj: el recorrido normal pasa por
 * En ruta → Giro próximo → Gira ahora → Llegaste, el del desvío por Fuera de ruta → recuperada, y
 * el del hueco por Sin señal → recuperada, sin parpadeos de estado.
 */
class DemoGpxNavigationTest {

    private val route = routeFromGpx(readGpx("ruta_parque_narino_a_parque_bolivar.gpx"))
    private val turns = route.steps.count { it.maneuver == Maneuver.LEFT || it.maneuver == Maneuver.RIGHT }

    private fun walk(gpx: String) = replay(route, readGpx(gpx))

    private fun Replay.describe() = runs.joinToString("\n") { "${it.status} ${it.durationMs / 1000} s" }

    /** Ningún estado se mete menos de [MIN_STABLE_MS] entre dos iguales (p. ej. En ruta → Fuera → En ruta). */
    private fun assertNoFlicker(replay: Replay) {
        replay.runs.windowed(3).forEach { (before, middle, after) ->
            val flicker = before.status == after.status && middle.durationMs < MIN_STABLE_MS
            assertTrue("Parpadeo: ${before.status} → ${middle.status} (${middle.durationMs} ms) → ${after.status}\n${replay.describe()}", !flicker)
        }
    }

    /** Cada giro se avisa una vez y en orden: Giro próximo y luego Gira ahora, nunca al revés. */
    private fun assertTurnsInOrder(replay: Replay) {
        replay.runs.zipWithNext().forEach { (current, next) ->
            if (current.status == TURN_APPROACHING) {
                assertEquals("Tras Giro próximo viene Gira ahora\n${replay.describe()}", MANEUVER, next.status)
            }
            if (current.status == MANEUVER) {
                assertTrue("Tras Gira ahora: ${next.status}\n${replay.describe()}", next.status in setOf(ON_ROUTE, TURN_APPROACHING, ARRIVED))
            }
        }
    }

    @Test
    fun theDemoRouteHasRealTurns() {
        // Calles en cuadrícula: 12 giros de ~90° entre el centro y el Parque Bolívar.
        assertEquals(12, turns)
        assertEquals(1970.0, route.distanceM, 5.0)
    }

    @Test
    fun normalWalkGoesOnRouteTurnApproachingTurnNowAndArrives() {
        val replay = walk("ruta_parque_narino_a_parque_bolivar.gpx")

        assertEquals(ON_ROUTE, replay.statuses.first())
        assertEquals(ARRIVED, replay.statuses.last())
        val firstTurn = replay.statuses.indexOf(TURN_APPROACHING)
        assertTrue(replay.describe(), firstTurn > 0)
        assertEquals(listOf(ON_ROUTE, TURN_APPROACHING, MANEUVER), replay.statuses.subList(firstTurn - 1, firstTurn + 2))
        assertTrue("Nunca fuera de ruta ni sin señal\n${replay.describe()}", replay.statuses.none { it == OFF_ROUTE || it == NO_SIGNAL })

        // Un aviso de giro próximo y uno de "gira ahora" por cada giro, y una sola llegada.
        assertEquals(turns, replay.countOf<NavigationEvent.TurnApproaching>())
        assertEquals(turns, replay.countOf<NavigationEvent.TurnNow>())
        assertEquals(turns, replay.statuses.count { it == MANEUVER })
        assertEquals(1, replay.count(NavigationEvent.Arrived))
        assertTurnsInOrder(replay)
        assertNoFlicker(replay)
    }

    @Test
    fun detourGoesOffRouteOnceAsksToRecalculateAndRecovers() {
        val replay = walk("ruta_con_desvio.gpx")

        assertEquals("Un solo tramo fuera de ruta\n${replay.describe()}", 1, replay.statuses.count { it == OFF_ROUTE })
        val off = replay.statuses.indexOf(OFF_ROUTE)
        assertTrue(replay.statuses[off - 1] in ON_ROUTE_STATES)
        assertTrue("Recuperada después del desvío\n${replay.describe()}", replay.statuses[off + 1] in ON_ROUTE_STATES)
        // ~40 m a 1,4 m/s lejos de la ruta: dura lo suficiente para pedir al menos un recálculo.
        assertTrue(replay.runs[off].durationMs >= 10_000)

        assertEquals(1, replay.count(NavigationEvent.OffRoute))
        assertEquals(1, replay.count(NavigationEvent.BackOnRoute))
        assertTrue(replay.count(NavigationEvent.RecalculateRequested) >= 1)
        assertTrue(replay.statuses.none { it == NO_SIGNAL })
        assertEquals(ARRIVED, replay.statuses.last())
        assertEquals(1, replay.count(NavigationEvent.Arrived))
        assertTurnsInOrder(replay)
        assertNoFlicker(replay)
    }

    @Test
    fun signalGapShowsNoSignalOnceAndRecovers() {
        val replay = walk("ruta_sin_senal.gpx")

        assertEquals("Un solo tramo sin señal\n${replay.describe()}", 1, replay.statuses.count { it == NO_SIGNAL })
        val lost = replay.statuses.indexOf(NO_SIGNAL)
        assertTrue(replay.statuses[lost - 1] in ON_ROUTE_STATES)
        assertTrue("Recuperada después del hueco\n${replay.describe()}", replay.statuses[lost + 1] in ON_ROUTE_STATES)

        assertEquals(1, replay.count(NavigationEvent.SignalLost))
        assertEquals(1, replay.count(NavigationEvent.SignalRecovered))
        assertTrue("Sin falsos desvíos al volver la señal\n${replay.describe()}", replay.statuses.none { it == OFF_ROUTE })
        assertEquals(ARRIVED, replay.statuses.last())
        assertEquals(1, replay.count(NavigationEvent.Arrived))
        assertTurnsInOrder(replay)
        assertNoFlicker(replay)
    }

    @Test
    fun noisyGpsOnTheNormalWalkStillNeverLeavesTheRoute() {
        // Lecturas a 8 m a un lado y al otro de la calle, alternadas: el filtro y la histéresis aguantan.
        val noisy = readGpx("ruta_parque_narino_a_parque_bolivar.gpx").mapIndexed { i, p ->
            val offsetDeg = if (i % 2 == 0) 0.00007 else -0.00007
            p.copy(lat = p.lat + offsetDeg, lng = p.lng - offsetDeg)
        }

        val replay = replay(route, noisy)

        assertTrue(replay.describe(), replay.statuses.none { it == OFF_ROUTE })
        assertEquals(ARRIVED, replay.statuses.last())
        assertNoFlicker(replay)
    }
}
