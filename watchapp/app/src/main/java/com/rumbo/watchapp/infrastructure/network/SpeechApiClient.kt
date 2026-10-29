package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.error.QuotaExhaustedError
import org.json.JSONObject

// Con el backend ya despierto una frase no debe tardar más; si tarda, ya no sirve decirla.
private const val AWAKE_TIMEOUT_MS = 20_000
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val QUOTA_EXHAUSTED = "QUOTA_EXHAUSTED"

/** Pide a POST /speech la frase hablada y devuelve el WAV. Cuota agotada → [QuotaExhaustedError]. */
object SpeechApiClient {

    suspend fun synthesize(text: String): ByteArray {
        val timeout = if (backendAwake) AWAKE_TIMEOUT_MS else TIMEOUT_MS
        // Sin reintento: una frase que llega tarde ya no sirve; la navegación sigue sin ella.
        val response = backendPostJson("/speech", JSONObject().put("text", text), timeout, retry = false)
        if (response.code == HTTP_TOO_MANY_REQUESTS || response.errorField("code") == QUOTA_EXHAUSTED) {
            throw QuotaExhaustedError(response.errorField("message") ?: "Se agotó la cuota de voz de Gemini")
        }
        response.requireOk()
        if (response.bytes.isEmpty()) throw AppError("El servidor envió un audio vacío")
        return response.bytes
    }
}
