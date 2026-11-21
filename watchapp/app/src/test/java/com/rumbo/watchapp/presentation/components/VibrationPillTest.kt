package com.rumbo.watchapp.presentation.components

import com.rumbo.watchapp.domain.vibration.VibrationCue
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.presentation.format.remainingWalkingMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationPillTest {

    @Test
    fun thePillSaysHowItFeelsAndWhichCueItIs() {
        val left = VibrationPatterns.of(VibrationCue.LEFT, supportsAmplitude = true)
        assertEquals("${left.description} · ${left.name.lowercase()}", vibrationPillText(left))
        assertTrue(vibrationPillText(left).endsWith("· izquierda"))
    }

    @Test
    fun longerPulsesDrawWiderBarsBetweenFourAndEighteenDp() {
        assertEquals(4f, pulseBarWidthDp(10), 0.001f)
        assertEquals(18f, pulseBarWidthDp(2_000), 0.001f)
        assertTrue(pulseBarWidthDp(100) < pulseBarWidthDp(400))
    }

    @Test
    fun remainingMinutesFollowTheRoutePaceAndRoundUp() {
        // 1 791 m en 1 433 s: faltan 466 m → 373 s → 7 min.
        assertEquals(7, remainingWalkingMinutes(466.0, 1791.0, 1433.0))
        assertEquals(1, remainingWalkingMinutes(0.0, 1791.0, 1433.0))
        assertNull(remainingWalkingMinutes(466.0, 0.0, 1433.0))
        assertNull(remainingWalkingMinutes(466.0, 1791.0, 0.0))
    }
}
