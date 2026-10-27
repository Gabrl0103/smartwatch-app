package com.rumbo.watchapp.application.location

import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.navigation.originFrom
import com.rumbo.watchapp.domain.port.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Única suscripción al GPS de la app: reparte las lecturas a quien las necesite y recuerda
 * la última para usarla como origen de la ruta. Vive en [scope] (de toda la app), así no se
 * corta si la pantalla que lo encendió desaparece.
 */
class LocationTracker(
    private val provider: LocationProvider,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) {
    private val _fixes = MutableSharedFlow<LocationFix>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val fixes: SharedFlow<LocationFix> = _fixes.asSharedFlow()

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

    /** Última ubicación real si es reciente y precisa; si no, el centro de Pasto. */
    fun origin(): Coordinates = originFrom(lastFix, clock())
}
