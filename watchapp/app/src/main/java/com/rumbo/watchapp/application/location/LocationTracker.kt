package com.rumbo.watchapp.application.location

import com.rumbo.watchapp.domain.error.GpsUnavailableError
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.navigation.originFrom
import com.rumbo.watchapp.domain.port.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Lo que se espera la primera lectura buena antes de rendirse; un GPS en frío puede tardar.
private const val ORIGIN_TIMEOUT_MS = 40_000L

/**
 * Única suscripción al GPS de la app: reparte las lecturas a quien las necesite y recuerda
 * la última para usarla como origen de la ruta. Vive en [scope] (de toda la app), así no se
 * corta si la pantalla que lo encendió desaparece.
 */
class LocationTracker(
    private val provider: LocationProvider,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val originTimeoutMs: Long = ORIGIN_TIMEOUT_MS,
) {
    private val _fixes = MutableSharedFlow<LocationFix>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val fixes: SharedFlow<LocationFix> = _fixes.asSharedFlow()

    private val _searching = MutableStateFlow(false)

    /** true mientras se espera una lectura buena para pedir la ruta ("Buscando señal GPS"). */
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    @Volatile
    private var lastFix: LocationFix? = null
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            provider.locations().collect { fix ->
                lastFix = fix
                _fixes.tryEmit(fix)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Ubicación real reciente y precisa para pedir la ruta. Si aún no la hay, enciende el GPS y
     * la espera; si no llega a tiempo, falla con [GpsUnavailableError]: nunca un origen inventado.
     */
    suspend fun awaitOrigin(): Coordinates {
        currentOrigin()?.let { return it }
        _searching.value = true
        try {
            return withTimeoutOrNull(originTimeoutMs) {
                // Primero se suscribe y luego enciende el GPS: así no se pierde la primera lectura.
                fixes.onSubscription {
                    start()
                    lastFix?.let { emit(it) }
                }.mapNotNull { currentOrigin() }.first()
            } ?: throw GpsUnavailableError()
        } finally {
            _searching.value = false
        }
    }

    private fun currentOrigin(): Coordinates? = originFrom(lastFix, clock())
}
