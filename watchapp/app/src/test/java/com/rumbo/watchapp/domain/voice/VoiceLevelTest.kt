package com.rumbo.watchapp.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLevelTest {

    @Test
    fun silenceIsZeroAndTheLoudestIsOne() {
        assertEquals(0f, amplitudeToLevel(0), 0f)
        assertEquals(0f, amplitudeToLevel(-5), 0f)
        assertEquals(0f, amplitudeToLevel(80), 0f)
        assertEquals(1f, amplitudeToLevel(32_767), 1e-4f)
    }

    @Test
    fun scaleIsLogarithmic() {
        // Cada vez que la amplitud se multiplica por 10 (20 dB) el nivel sube lo mismo: 0,4.
        val quiet = amplitudeToLevel(330)
        val normal = amplitudeToLevel(3_300)
        val loud = amplitudeToLevel(32_767)
        assertEquals(0.4f, normal - quiet, 0.01f)
        assertEquals(0.4f, loud - normal, 0.01f)
        // Una voz normal ya queda por encima de la mitad.
        assertTrue(amplitudeToLevel(3_000) > 0.5f)
    }

    @Test
    fun levelGrowsWithAmplitude() {
        val levels = listOf(100, 300, 1_000, 3_000, 10_000, 30_000).map(::amplitudeToLevel)
        assertTrue(levels.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun smootherRisesFastAndFallsSlowly() {
        val smoother = LevelSmoother()
        val up = smoother.next(1f)
        assertEquals(0.6f, up, 1e-6f)
        val down = smoother.next(0f)
        // Baja solo un cuarto del camino: en la pausa entre dos palabras las barras no se apagan.
        assertEquals(0.45f, down, 1e-6f)
        repeat(20) { smoother.next(0f) }
        assertTrue(smoother.next(0f) < 0.01f)
    }
}
