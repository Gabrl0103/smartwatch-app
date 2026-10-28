package com.rumbo.watchapp.domain.port

interface DeviceRepository {
    /** Registra el reloj en el backend; repetirlo con el mismo id no duplica. */
    suspend fun register(deviceId: String)
}
