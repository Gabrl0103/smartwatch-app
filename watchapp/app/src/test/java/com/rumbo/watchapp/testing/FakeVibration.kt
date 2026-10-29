package com.rumbo.watchapp.testing

import com.rumbo.watchapp.domain.port.VibrationController
import com.rumbo.watchapp.domain.vibration.VibrationCue
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.domain.vibration.VibrationPatterns

/** Motor de vibración falso: anota lo que vibra y cuántas veces se detuvo. */
class FakeVibration(override val supportsAmplitude: Boolean = true) : VibrationController {
    val played = mutableListOf<VibrationPattern>()
    var stops = 0

    override fun play(pattern: VibrationPattern) {
        played += pattern
    }

    override fun stop() {
        stops++
    }
}

/** El patrón de [cue] en la versión con amplitud, la del [FakeVibration] por defecto. */
fun pattern(cue: VibrationCue, supportsAmplitude: Boolean = true): VibrationPattern =
    VibrationPatterns.of(cue, supportsAmplitude)
