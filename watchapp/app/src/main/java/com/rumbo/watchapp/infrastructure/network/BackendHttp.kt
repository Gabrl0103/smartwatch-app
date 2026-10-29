package com.rumbo.watchapp.infrastructure.network

import android.util.Log
import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.error.NoConnectionError
import com.rumbo.watchapp.domain.error.ServerWakingError
import com.rumbo.watchapp.domain.port.ServerStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// Render duerme el servicio gratis; la primera petición puede tardar en despertarlo.
internal const val TIMEOUT_MS = 90_000
private const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
private const val TAG = "Red"
private const val SLOW_SERVER_MESSAGE = "El servidor tardó demasiado en responder, intenta de nuevo"

// Si el servidor aún no contestó nunca y tarda más que esto, se avisa que está despertando.
private const val WAKING_NOTICE_MS = 5_000L
internal const val RETRY_DELAY_MS = 2_000L

// Lo que responde el proxy de Render mientras arranca el servicio (sin el JSON de error propio).
private val GATEWAY_CODES = setOf(502, 503, 504)

/** true desde que el backend respondió una vez en este proceso: ya no está dormido. */
@Volatile
internal var backendAwake = false
    private set

/** Cuenta las peticiones que esperan a que Render despierte el servidor, para avisarlo en pantalla. */
object BackendServerStatus : ServerStatus {
    private val waiting = AtomicInteger(0)
    private val _waking = MutableStateFlow(false)
    override val waking: StateFlow<Boolean> = _waking.asStateFlow()

    internal fun begin() {
        _waking.value = waiting.incrementAndGet() > 0
    }

    internal fun end() {
        _waking.value = waiting.decrementAndGet() > 0
    }
}

internal class HttpResponse(val code: Int, val bytes: ByteArray) {
    val body: String get() = bytes.toString(Charsets.UTF_8)
}

/**
 * Hace una petición al backend y devuelve el código y el cuerpo, sea éxito o error. Sin internet
 * falla con [NoConnectionError] y con el servidor dormido con [ServerWakingError]; en ambos casos,
 * si [retry], lo intenta una vez más tras [RETRY_DELAY_MS]. Cada resultado queda en Logcat ("Red").
 */
internal suspend fun backendRequest(
    method: String,
    path: String,
    contentType: String? = null,
    body: ByteArray? = null,
    timeoutMs: Int = TIMEOUT_MS,
    retry: Boolean = true,
): HttpResponse {
    val name = "$method ${path.substringBefore('?')}"
    return withOneRetry(
        enabled = retry,
        onRetry = { Log.w(TAG, "$name: ${it.message}. Reintento en ${RETRY_DELAY_MS / 1000} s") },
    ) { previous ->
        // En el reintento tras un servidor dormido el aviso sale de inmediato: ya se sabe que despierta.
        val noticeMs = if (previous is ServerWakingError) 0L else WAKING_NOTICE_MS
        requestOnce(name, path, method, contentType, body, timeoutMs, noticeMs)
    }
}

/**
 * Corre [block]; si falla por algo pasajero ([isTransient]) y [enabled], espera [delayMs] y lo
 * corre una sola vez más, pasándole el error anterior. Otros errores salen tal cual.
 */
internal suspend fun <T> withOneRetry(
    enabled: Boolean,
    delayMs: Long = RETRY_DELAY_MS,
    onRetry: (AppError) -> Unit = {},
    block: suspend (previous: AppError?) -> T,
): T {
    val first = try {
        return block(null)
    } catch (e: AppError) {
        if (!enabled || !e.isTransient()) throw e
        e
    }
    onRetry(first)
    delay(delayMs)
    return block(first)
}

internal fun AppError.isTransient() = this is NoConnectionError || this is ServerWakingError

/** Traduce una falla de red al error que ve el usuario. [awake]: el servidor ya contestó antes. */
internal fun networkError(e: IOException, awake: Boolean): AppError = when (e) {
    is SocketTimeoutException -> if (awake) AppError(SLOW_SERVER_MESSAGE) else ServerWakingError()
    // UnknownHost (sin DNS), Connect/NoRouteToHost y cortes: para el usuario es no tener internet.
    else -> NoConnectionError()
}

private suspend fun requestOnce(
    name: String,
    path: String,
    method: String,
    contentType: String?,
    body: ByteArray?,
    timeoutMs: Int,
    noticeMs: Long,
): HttpResponse = coroutineScope {
    val notice = if (backendAwake) {
        null
    } else {
        launch {
            delay(noticeMs)
            BackendServerStatus.begin()
            try {
                awaitCancellation()
            } finally {
                BackendServerStatus.end()
            }
        }
    }
    try {
        withContext(Dispatchers.IO) { send(name, path, method, contentType, body, timeoutMs) }
    } finally {
        notice?.cancel()
    }
}

private fun send(
    name: String,
    path: String,
    method: String,
    contentType: String?,
    body: ByteArray?,
    timeoutMs: Int,
): HttpResponse {
    val startNs = System.nanoTime()
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
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = HttpResponse(code, stream?.use { it.readBytes() } ?: ByteArray(0))
        val ms = (System.nanoTime() - startNs) / 1_000_000
        if (code in GATEWAY_CODES && response.errorField("code") == null) {
            Log.w(TAG, "$name → $code en $ms ms: el servidor está despertando")
            throw ServerWakingError()
        }
        backendAwake = true
        if (code in 200..299) Log.i(TAG, "$name → $code en $ms ms") else Log.w(TAG, "$name → $code en $ms ms")
        return response
    } catch (e: IOException) {
        val error = networkError(e, backendAwake)
        Log.w(TAG, "$name falló (${e.javaClass.simpleName}): ${error.message}")
        throw error
    } finally {
        connection.disconnect()
    }
}

/** Envía [json] en el cuerpo de un POST. */
internal suspend fun backendPostJson(
    path: String,
    json: JSONObject,
    timeoutMs: Int = TIMEOUT_MS,
    retry: Boolean = true,
): HttpResponse =
    backendRequest("POST", path, JSON_CONTENT_TYPE, json.toString().toByteArray(), timeoutMs, retry)

/** Campo de {"error": {...}} del backend (p. ej. "code" o "message"), si viene. */
internal fun HttpResponse.errorField(key: String): String? = runCatching {
    JSONObject(body).getJSONObject("error").getString(key)
}.getOrNull()?.ifBlank { null }

/** Devuelve el cuerpo si la respuesta es 2xx; si no, lanza el mensaje de error del backend. */
internal fun HttpResponse.requireOk(): String {
    if (code in 200..299) return body
    throw AppError(errorField("message") ?: "El servidor respondió con error $code")
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
