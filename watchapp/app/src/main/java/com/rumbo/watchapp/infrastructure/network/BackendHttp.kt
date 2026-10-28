package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.error.AppError
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// Render duerme el servicio gratis; la primera petición puede tardar en despertarlo.
internal const val TIMEOUT_MS = 90_000
private const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"

/** true desde que el backend respondió una vez en este proceso: ya no está dormido. */
@Volatile
internal var backendAwake = false
    private set

internal class HttpResponse(val code: Int, val bytes: ByteArray) {
    val body: String get() = bytes.toString(Charsets.UTF_8)
}

/** Hace una petición al backend y devuelve el código y el cuerpo, sea éxito o error. */
internal suspend fun backendRequest(
    method: String,
    path: String,
    contentType: String? = null,
    body: ByteArray? = null,
    timeoutMs: Int = TIMEOUT_MS,
): HttpResponse = withContext(Dispatchers.IO) {
    val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = method
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        if (body != null) {
            connection.doOutput = true
            contentType?.let { connection.setRequestProperty("Content-Type", it) }
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        backendAwake = true
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        HttpResponse(code, stream?.use { it.readBytes() } ?: ByteArray(0))
    } catch (e: SocketTimeoutException) {
        throw AppError("El servidor tardó demasiado en responder, intenta de nuevo")
    } catch (e: IOException) {
        throw AppError("No pude conectar con el servidor, revisa la conexión")
    } finally {
        connection.disconnect()
    }
}

/** Envía [json] en el cuerpo de un POST. */
internal suspend fun backendPostJson(
    path: String,
    json: JSONObject,
    timeoutMs: Int = TIMEOUT_MS,
): HttpResponse = backendRequest("POST", path, JSON_CONTENT_TYPE, json.toString().toByteArray(), timeoutMs)

/** Devuelve el cuerpo si la respuesta es 2xx; si no, lanza el mensaje de error del backend. */
internal fun HttpResponse.requireOk(): String {
    if (code in 200..299) return body
    val serverMessage = runCatching {
        JSONObject(body).getJSONObject("error").getString("message")
    }.getOrNull()
    throw AppError(
        if (serverMessage.isNullOrBlank()) "El servidor respondió con error $code" else serverMessage,
    )
}

internal fun <T> parseJson(body: String, parse: (JSONObject) -> T): T =
    try {
        parse(JSONObject(body))
    } catch (e: JSONException) {
        throw AppError("El servidor envió una respuesta inesperada")
    }

internal fun <T> parseJsonArray(body: String, parse: (JSONObject) -> T): List<T> =
    try {
        val array = JSONArray(body)
        List(array.length()) { i -> parse(array.getJSONObject(i)) }
    } catch (e: JSONException) {
        throw AppError("El servidor envió una respuesta inesperada")
    }

internal fun JSONObject.optionalString(key: String): String? =
    if (isNull(key)) null else getString(key).ifBlank { null }
