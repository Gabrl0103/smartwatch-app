package com.rumbo.watchapp.infrastructure.navigation

import android.content.Context
import android.content.Intent
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.port.NavigationRunner

/** Corre la navegación dentro de [NavigationForegroundService]. */
class ForegroundNavigationRunner(private val context: Context) : NavigationRunner {

    /** Ruta que el servicio debe seguir; la lee al arrancar. */
    @Volatile
    var currentRoute: Route? = null
        private set

    // Se llama al tocar "Ir", con la app en primer plano: Android permite iniciar el servicio.
    override fun start(route: Route) {
        currentRoute = route
        context.startService(Intent(context, NavigationForegroundService::class.java))
    }

    override fun stop() {
        currentRoute = null
        context.stopService(Intent(context, NavigationForegroundService::class.java))
    }
}
