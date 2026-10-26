package com.rumbo.watchapp.domain.model

/** Lo que el backend entendió de una orden de voz. */
data class VoiceResult(
    val text: String,
    val intent: String,
    val destination: String?,
    val question: String?,
)
