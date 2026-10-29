package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.vibration.VibrationSettings

/** Ajustes "Intensidad" y "Duración" de las vibraciones; se recuerdan entre arranques. */
interface VibrationSettingsStore {
    fun load(): VibrationSettings

    fun save(settings: VibrationSettings)
}
