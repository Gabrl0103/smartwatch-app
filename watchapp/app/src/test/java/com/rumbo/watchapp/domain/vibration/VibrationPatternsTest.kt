package com.rumbo.watchapp.domain.vibration

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.navigation.NavigationEvent
import com.rumbo.watchapp.testing.lRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationPatternsTest {

    private val all = with(VibrationPatterns) {
        listOf(ON_ROUTE, TURN_APPROACHING, LEFT, RIGHT, OFF_ROUTE, NO_SIGNAL, ARRIVED)
    }

    @Test
    fun everyPatternAlternatesPauseAndVibration() {
        all.forEach { pattern ->
            pattern.amplitudes.forEachIndexed { i, amplitude ->
                if (i % 2 == 0) assertEquals(pattern.label, 0, amplitude)
                else assertTrue(pattern.label, amplitude in 1..255)
            }
        }
    }

    @Test
    fun patternsMatchTheSpecification() = with(VibrationPatterns) {
        assertEquals(listOf(300L), ON_ROUTE.pulseDurationsMs)
        assertEquals(listOf(60), ON_ROUTE.pulseAmplitudes)
        assertEquals(6_000L, ON_ROUTE.repeatEveryMs)

        assertEquals(listOf(100L, 100L), TURN_APPROACHING.pulseDurationsMs)
        assertEquals(listOf(255, 255), TURN_APPROACHING.pulseAmplitudes)

        assertEquals(listOf(300L, 300L), LEFT.pulseDurationsMs)
        assertEquals(listOf(150, 150), LEFT.pulseAmplitudes)

        assertEquals(listOf(700L), RIGHT.pulseDurationsMs)
        assertEquals(listOf(150), RIGHT.pulseAmplitudes)

        assertEquals(listOf(500L, 500L, 500L), OFF_ROUTE.pulseDurationsMs)
        assertEquals(listOf(200, 200, 200), OFF_ROUTE.pulseAmplitudes)

        assertEquals(listOf(600L), NO_SIGNAL.pulseDurationsMs)
        assertEquals(listOf(150), NO_SIGNAL.pulseAmplitudes)
        assertEquals(5_000L, NO_SIGNAL.repeatEveryMs)

        assertEquals(listOf(100L, 200L, 300L, 400L, 500L), ARRIVED.pulseDurationsMs)
        assertTrue(ARRIVED.pulseAmplitudes.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun pulsesKeepTheirPlaceInThePattern() {
        assertEquals(
            listOf(Pulse(0, 300, 150), Pulse(500, 300, 150)),
            VibrationPatterns.LEFT.pulses,
        )
        all.forEach { pattern ->
            assertEquals(pattern.label, pattern.pulseDurationsMs, pattern.pulses.map { it.durationMs })
            assertTrue(pattern.label, pattern.pulses.all { it.startMs + it.durationMs <= pattern.durationMs })
        }
    }

    @Test
    fun leftAndRightFeelDifferent() {
        assertTrue(VibrationPatterns.LEFT.pulseDurationsMs != VibrationPatterns.RIGHT.pulseDurationsMs)
    }

    @Test
    fun eventsMapToPatterns() = with(VibrationPatterns) {
        assertEquals(TURN_APPROACHING, forEvent(NavigationEvent.TurnApproaching(Maneuver.LEFT)))
        assertEquals(LEFT, forEvent(NavigationEvent.TurnNow(Maneuver.LEFT)))
        assertEquals(RIGHT, forEvent(NavigationEvent.TurnNow(Maneuver.RIGHT)))
        assertEquals(OFF_ROUTE, forEvent(NavigationEvent.OffRoute))
        assertEquals(NO_SIGNAL, forEvent(NavigationEvent.SignalLost))
        assertEquals(ARRIVED, forEvent(NavigationEvent.Arrived))
        assertEquals(ON_ROUTE, forEvent(NavigationEvent.BackOnRoute))
        assertNull(forEvent(NavigationEvent.RecalculateRequested))
        assertNull(forEvent(NavigationEvent.NextStepChanged(lRoute().steps[1])))
    }

    @Test
    fun mostImportantPatternWins() {
        val events = listOf(NavigationEvent.TurnNow(Maneuver.RIGHT), NavigationEvent.Arrived)
        assertEquals(VibrationPatterns.ARRIVED, VibrationPatterns.mostImportant(events))
        assertNull(VibrationPatterns.mostImportant(emptyList()))
    }
}
