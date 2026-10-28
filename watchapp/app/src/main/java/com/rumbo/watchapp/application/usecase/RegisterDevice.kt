package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.port.DeviceRepository
import com.rumbo.watchapp.domain.port.DeviceStore

/** Registra el reloj en el backend una sola vez; si falla, se reintenta en el próximo inicio. */
class RegisterDevice(
    private val devices: DeviceRepository,
    private val store: DeviceStore,
) {
    suspend operator fun invoke() {
        if (store.isRegistered()) return
        devices.register(store.deviceId())
        store.markRegistered()
    }
}
