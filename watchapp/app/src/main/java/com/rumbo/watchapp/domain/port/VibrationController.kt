package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.vibration.VibrationPattern

interface VibrationController {
    /** Reproduce una vez el patrón, cortando el que esté sonando. */
    fun play(pattern: VibrationPattern)

    fun stop()
}
