package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.model.Route

/** Arranca y detiene la navegación de forma que siga viva con la pantalla apagada. */
interface NavigationRunner {
    /** Ruta que se está navegando ahora (aunque la pantalla se haya cerrado), o null. */
    val activeRoute: Route?

    fun start(route: Route)

    /** Detiene todo: GPS, vibraciones, voz, servicio y notificación. */
    fun stop()

    /** El destino de la ruta en curso se acaba de guardar. */
    fun markDestinationSaved()
}
