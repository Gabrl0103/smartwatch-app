package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.error.AppError
import org.json.JSONObject

// Con el backend ya despierto una frase no debe tardar más; si tarda, mejor la voz del sistema.
private const val AWAKE_TIMEOUT_MS = 20_000

/** Pide a POST /speech la frase hablada y devuelve el WAV. */
object SpeechApiClient {

    suspend fun synthesize(text: String): ByteArray {
        val timeout = if (backendAwake) AWAKE_TIMEOUT_MS else TIMEOUT_MS
        val response = backendPostJson("/speech", JSONObject().put("text", text), timeout)
        response.requireOk()
        if (response.bytes.isEmpty()) throw AppError("El servidor envió un audio vacío")
        return response.bytes
    }
}
