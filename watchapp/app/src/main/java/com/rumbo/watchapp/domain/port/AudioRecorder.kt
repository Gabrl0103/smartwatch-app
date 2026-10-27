package com.rumbo.watchapp.domain.port

import java.io.File

interface AudioRecorder {
    /** Empieza a grabar desde el micrófono. */
    fun start()

    /** Detiene la grabación y devuelve el archivo con el audio. */
    fun stop(): File

    /** Libera el micrófono si quedó en uso. */
    fun release()
}
