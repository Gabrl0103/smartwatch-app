package com.rumbo.watchapp.application.map

import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Pone ritmo a las peticiones: como mucho [maxPerSecond] empiezan por segundo y [maxConcurrent]
 * van a la vez. Las que sobran esperan su turno (suspendidas, sin bloquear el hilo).
 */
class RequestPacer(maxPerSecond: Int, maxConcurrent: Int, private val clock: () -> Long) {
    private val intervalMs = 1_000L / maxPerSecond
    private val slots = Semaphore(maxConcurrent)
    private val turn = Mutex()
    private var nextStartMs = Long.MIN_VALUE

    suspend fun <T> run(block: suspend () -> T): T = slots.withPermit {
        turn.withLock {
            val now = clock()
            if (nextStartMs > now) delay(nextStartMs - now)
            nextStartMs = max(now, nextStartMs) + intervalMs
        }
        block()
    }
}
