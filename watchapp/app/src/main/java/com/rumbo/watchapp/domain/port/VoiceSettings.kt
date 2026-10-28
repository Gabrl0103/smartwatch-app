package com.rumbo.watchapp.domain.port

/** Preferencia "Voz: sí/no" del usuario; se recuerda entre arranques. */
interface VoiceSettings {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}
