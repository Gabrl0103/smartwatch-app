package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.vibration.VibrationPattern

interface VibrationController {
    /** Si el motor puede variar la fuerza; si no, solo enciende y apaga y vale solo el ritmo. */
    val supportsAmplitude: Boolean

    /** Reproduce una vez el patrón, cortando el que esté sonando. */
    fun play(pattern: VibrationPattern)

    fun stop()
}
