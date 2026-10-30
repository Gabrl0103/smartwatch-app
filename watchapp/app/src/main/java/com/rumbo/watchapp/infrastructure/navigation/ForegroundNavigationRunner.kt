package com.rumbo.watchapp.infrastructure.navigation

import android.content.Context
import android.content.Intent
import android.util.Log
import com.rumbo.watchapp.application.location.LocationTracker
import com.rumbo.watchapp.application.usecase.TrackNavigation
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.port.NavigationRunner
import kotlinx.coroutines.CoroutineScope

private const val TAG = "Nav"

/**
 * Corre la navegación dentro de [NavigationForegroundService]. Si Android no deja iniciar el
 * servicio, navega igual dentro de la app ([fallbackScope]) en vez de fallar.
 */
class ForegroundNavigationRunner(
    private val context: Context,
    private val trackNavigation: TrackNavigation,
    private val locationTracker: LocationTracker,
    private val fallbackScope: CoroutineScope,
) : NavigationRunner {

    /** Ruta que el servicio debe seguir; la lee al arrancar. */
    @Volatile
    override var activeRoute: Route? = null
        private set

    // Se llama al tocar "Ir" o "Continuar", con la app en primer plano: Android permite iniciar el servicio.
    override fun start(route: Route) {
        activeRoute = route
        try {
            context.startService(serviceIntent())
        } catch (e: IllegalStateException) {
            Log.w(TAG, "No se pudo iniciar el servicio (${e.message}); navego dentro de la app")
            locationTracker.start()
            trackNavigation.start(route, fallbackScope)
        }
    }

    // Todo se corta ya, sin esperar a que Android destruya el servicio (que además quita la notificación).
    override fun stop() {
        activeRoute = null
        trackNavigation.stop()
        locationTracker.stop()
        context.stopService(serviceIntent())
    }

    override fun markDestinationSaved() {
        activeRoute = activeRoute?.copy(destinationSaved = true)
        trackNavigation.markDestinationSaved()
    }

    private fun serviceIntent() = Intent(context, NavigationForegroundService::class.java)
}
