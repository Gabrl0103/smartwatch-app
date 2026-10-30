package com.rumbo.watchapp.infrastructure.tts

import java.security.MessageDigest

/**
 * Versión de los audios guardados. Subirla cuando cambie cómo genera la voz el backend, para no
 * reutilizar WAV viejos. v2: la v1 leía en voz alta la instrucción de estilo antes de la frase.
 */
internal const val SPEECH_CACHE_VERSION = "v2"

/** Nombre del WAV en cache para [text]: "v2-<sha256 de versión + texto>.wav". */
internal fun speechCacheFileName(text: String): String {
    val hash = MessageDigest.getInstance("SHA-256")
        .digest("$SPEECH_CACHE_VERSION:$text".toByteArray())
        .joinToString("") { "%02x".format(it) }
    return "$SPEECH_CACHE_VERSION-$hash.wav"
}

/** Sufijo de un audio a medio escribir. */
internal const val SPEECH_PARTIAL_SUFFIX = ".part"

/** true si el archivo es de otra versión o quedó a medias, y hay que borrarlo. */
internal fun isStaleSpeechFile(fileName: String): Boolean =
    !fileName.startsWith("$SPEECH_CACHE_VERSION-") || fileName.endsWith(SPEECH_PARTIAL_SUFFIX)
