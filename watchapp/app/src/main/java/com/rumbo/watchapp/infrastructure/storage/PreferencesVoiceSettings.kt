package com.rumbo.watchapp.infrastructure.storage

import android.content.Context
import com.rumbo.watchapp.domain.port.VoiceSettings

private const val PREFERENCES = "rumbo_settings"
private const val KEY_VOICE_ENABLED = "voice_enabled"

/** Guarda "Voz: sí/no" en SharedPreferences. Empieza en sí. */
class PreferencesVoiceSettings(context: Context) : VoiceSettings {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = preferences.getBoolean(KEY_VOICE_ENABLED, true)

    override fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
    }
}
