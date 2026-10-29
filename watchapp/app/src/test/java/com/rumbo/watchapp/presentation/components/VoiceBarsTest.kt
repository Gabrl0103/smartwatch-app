package com.rumbo.watchapp.presentation.components

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceBarsTest {

    // Fases a lo largo de una vuelta de la onda.
    private val phases = (0 until 24).map { it * 2 * PI.toFloat() / 24 }

    @Test
    fun nineBarsAlwaysBetweenSixteenAndSeventySixDp() {
        listOf(0f, 0.3f, 0.7f, 1f, 1.5f, -0.2f).forEach { level ->
            phases.forEach { phase ->
                val heights = voiceBarHeights(level, phase)
                assertEquals(VOICE_BAR_COUNT, heights.size)
                assertTrue("$level $phase $heights", heights.all { it in 16f..76f })
            }
        }
    }

    @Test
    fun silenceLeavesEveryBarAtTheMinimum() {
        phases.forEach { phase -> assertEquals(List(9) { 16f }, voiceBarHeights(0f, phase)) }
    }

    @Test
    fun barsAreSymmetricWithTheCenterTallest() {
        listOf(0.2f, 0.6f, 1f).forEach { level ->
            phases.forEach { phase ->
                val heights = voiceBarHeights(level, phase)
                for (i in 0 until 4) assertEquals("$level $phase", heights[i], heights[8 - i], 1e-4f)
                assertTrue("$level $phase $heights", heights.withIndex().all { (i, h) -> i == 4 || h < heights[4] })
                // De los bordes al centro, cada barra es más alta que la de afuera.
                assertTrue("$level $phase $heights", heights.subList(0, 5).zipWithNext().all { (a, b) -> b > a })
            }
        }
    }

    @Test
    fun louderVoiceMakesTallerBars() {
        phases.forEach { phase ->
            val quiet = voiceBarHeights(0.3f, phase)
            val loud = voiceBarHeights(0.9f, phase)
            assertTrue(quiet.zip(loud).all { (q, l) -> l > q })
        }
        // La del centro llega casi al máximo con la voz más fuerte.
        assertTrue(phases.maxOf { voiceBarHeights(1f, it)[4] } > 75f)
    }

    @Test
    fun theWaveMovesTheBarsOverTime() {
        val start = voiceBarHeights(0.7f, 0f)
        val later = voiceBarHeights(0.7f, PI.toFloat() / 2)
        assertTrue(start.zip(later).any { (a, b) -> kotlin.math.abs(a - b) > 1f })
    }
}
