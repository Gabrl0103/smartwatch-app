package com.rumbo.watchapp.domain.port

import java.io.File
import kotlinx.coroutines.flow.Flow

interface AudioRecorder {
    /** Empieza a grabar desde el micrófono. */
    fun start()

    /**
     * Nivel de la voz de 0 a 1 mientras se graba, una lectura cada ~80 ms
     * ([com.rumbo.watchapp.domain.voice.LEVEL_SAMPLE_MS]), en escala logarítmica y suavizado.
     * Termina cuando se deja de grabar.
     */
    val levels: Flow<Float>

    /** Detiene la grabación y devuelve el archivo con el audio. */
    fun stop(): File

    /** Libera el micrófono si quedó en uso. */
    fun release()
}
