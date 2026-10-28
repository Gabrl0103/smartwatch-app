package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.port.DeviceRepository
import org.json.JSONObject

private const val DEVICE_TYPE = "wear_os"

/** Registra el reloj con POST /devices (idempotente en el backend). */
object DevicesApiClient : DeviceRepository {

    override suspend fun register(deviceId: String) {
        val request = JSONObject().put("device_id", deviceId).put("type", DEVICE_TYPE)
        backendPostJson("/devices", request).requireOk()
    }
}
