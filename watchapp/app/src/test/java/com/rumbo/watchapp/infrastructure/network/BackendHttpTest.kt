package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.error.NO_CONNECTION_MESSAGE
import com.rumbo.watchapp.domain.error.NoConnectionError
import com.rumbo.watchapp.domain.error.SERVER_WAKING_MESSAGE
import com.rumbo.watchapp.domain.error.ServerWakingError
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mensajes distintos para "sin internet" y "servidor dormido", con un solo reintento automático. */
class BackendHttpTest {

    private fun error(json: String) = HttpResponse(429, json.toByteArray())

    @Test
    fun a429SaysHowLongToWaitWhenTheBackendKnows() {
        val quota = quotaError(error("""{"error": {"code": "QUOTA_EXHAUSTED", "message": "Espera.", "retry_after_s": 27}}"""))

        assertEquals(27_000L, quota.retryAfterMs)
        assertEquals("Espera.", quota.message)
    }

    @Test
    fun a429FromAnOlderBackendHasNoWait() {
        val quota = quotaError(error("""{"error": {"code": "QUOTA_EXHAUSTED", "message": "Sin cuota."}}"""))

        assertNull(quota.retryAfterMs)
    }

    @Test
    fun a429WithoutOurErrorFormatStillIsAQuotaError() {
        val quota = quotaError(error("Too Many Requests"))

        assertNull(quota.retryAfterMs)
        assertEquals("Se agotó la cuota de voz de Gemini", quota.message)
    }

    @Test
    fun withoutDnsOrConnectionItIsNoConnection() {
        assertEquals(NO_CONNECTION_MESSAGE, networkError(UnknownHostException("smartwatch-app.onrender.com"), false).message)
        assertTrue(networkError(ConnectException("refused"), true) is NoConnectionError)
        assertTrue(networkError(IOException("reset"), true) is NoConnectionError)
    }

    @Test
    fun timeoutBeforeTheServerEverAnsweredMeansItIsWakingUp() {
        val error = networkError(SocketTimeoutException(), awake = false)

        assertTrue(error is ServerWakingError)
        assertEquals(SERVER_WAKING_MESSAGE, error.message)
    }

    @Test
    fun timeoutOnceTheServerIsAwakeIsJustSlow() {
        val error = networkError(SocketTimeoutException(), awake = true)

        assertEquals("El servidor tardó demasiado en responder, intenta de nuevo", error.message)
    }

    @Test
    fun transientFailureIsRetriedOnceWithThePreviousError() = runBlocking {
        val seen = mutableListOf<AppError?>()

        val result = withOneRetry(enabled = true, delayMs = 0) { previous ->
            seen += previous
            if (previous == null) throw ServerWakingError()
            "ok"
        }

        assertEquals("ok", result)
        assertNull(seen[0])
        assertTrue(seen[1] is ServerWakingError)
    }

    @Test
    fun secondFailureIsShownWithoutMoreRetries() {
        var calls = 0

        assertThrows(NoConnectionError::class.java) {
            runBlocking {
                withOneRetry<Unit>(enabled = true, delayMs = 0) {
                    calls++
                    throw NoConnectionError()
                }
            }
        }
        assertEquals(2, calls)
    }

    @Test
    fun backendErrorsAndDisabledRetryAreNotRetried() {
        var calls = 0
        val failing: suspend (AppError?) -> Unit = {
            calls++
            throw AppError("No encontré ese lugar")
        }

        assertThrows(AppError::class.java) { runBlocking { withOneRetry(enabled = true, delayMs = 0, block = failing) } }
        assertThrows(NoConnectionError::class.java) {
            runBlocking { withOneRetry<Unit>(enabled = false, delayMs = 0) { calls++; throw NoConnectionError() } }
        }
        assertEquals(2, calls)
    }
}
