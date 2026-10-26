package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.model.VoiceResult
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Render duerme el servicio gratis; la primera petición puede tardar en despertarlo.
private const val TIMEOUT_MS = 90_000

/** Envía el audio grabado (AAC) al backend para que lo transcriba e interprete. */
object VoiceApiClient {

    suspend fun interpret(audioFile: File): VoiceResult = withContext(Dispatchers.IO) {
        val connection = URL("$BASE_URL/voice/interpret").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "audio/aac")
            connection.outputStream.use { it.write(audioFile.readBytes()) }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw IllegalStateException(errorMessage(code, body))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseVoiceResult(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseVoiceResult(body: String): VoiceResult {
        val json = JSONObject(body)
        return VoiceResult(
            text = json.optString("text"),
            intent = json.optString("intent"),
            destination = json.optionalString("destination"),
            question = json.optionalString("question"),
        )
    }

    private fun errorMessage(code: Int, body: String?): String {
        val serverMessage = body?.let {
            runCatching { JSONObject(it).getJSONObject("error").getString("message") }.getOrNull()
        }
        val base = "El servidor respondió con código $code"
        return if (serverMessage.isNullOrBlank()) base else "$base: $serverMessage"
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else getString(key).ifBlank { null }
}
