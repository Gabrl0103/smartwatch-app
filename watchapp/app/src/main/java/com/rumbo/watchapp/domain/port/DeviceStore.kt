package com.rumbo.watchapp.domain.port

/** Datos del reloj guardados en el propio dispositivo. */
interface DeviceStore {
    /** Id único del reloj; se crea y guarda la primera vez que se pide. */
    fun deviceId(): String

    fun isRegistered(): Boolean

    fun markRegistered()
}
