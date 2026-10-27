package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.model.Route

/** Arranca y detiene la navegación de forma que siga viva con la pantalla apagada. */
interface NavigationRunner {
    fun start(route: Route)

    fun stop()
}
