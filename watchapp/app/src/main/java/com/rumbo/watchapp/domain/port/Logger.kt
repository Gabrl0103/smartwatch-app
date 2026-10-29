package com.rumbo.watchapp.domain.port

/** Registro para seguir la app en Logcat; [tag] agrupa los mensajes ("Nav", "Vibracion", "Red"). */
interface Logger {
    fun info(tag: String, message: String)

    fun warn(tag: String, message: String)
}

/** No registra nada: para pruebas y para quien no necesita log. */
object NoLogger : Logger {
    override fun info(tag: String, message: String) = Unit

    override fun warn(tag: String, message: String) = Unit
}
