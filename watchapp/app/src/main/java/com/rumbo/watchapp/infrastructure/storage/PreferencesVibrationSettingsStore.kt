package com.rumbo.watchapp.infrastructure.storage

import android.content.Context
import com.rumbo.watchapp.domain.port.VibrationSettingsStore
import com.rumbo.watchapp.domain.vibration.PulseDuration
import com.rumbo.watchapp.domain.vibration.VibrationIntensity
import com.rumbo.watchapp.domain.vibration.VibrationSettings

private const val KEY_INTENSITY = "vibration_intensity"
private const val KEY_DURATION = "vibration_duration"

/** Guarda "Intensidad" y "Duración" en SharedPreferences por nombre. Empiezan en normal. */
class PreferencesVibrationSettingsStore(context: Context) : VibrationSettingsStore {

    private val preferences = context.getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE)

    override fun load(): VibrationSettings {
        val defaults = VibrationSettings()
        // Un valor guardado que ya no existe vuelve al de por defecto en vez de fallar.
        val intensity = VibrationIntensity.entries.find { it.name == preferences.getString(KEY_INTENSITY, null) }
        val duration = PulseDuration.entries.find { it.name == preferences.getString(KEY_DURATION, null) }
        return VibrationSettings(intensity ?: defaults.intensity, duration ?: defaults.duration)
    }

    override fun save(settings: VibrationSettings) {
        preferences.edit()
            .putString(KEY_INTENSITY, settings.intensity.name)
            .putString(KEY_DURATION, settings.duration.name)
            .apply()
    }
}
