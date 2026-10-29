package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.model.Route

/**
 * Ruta en curso guardada en el reloj: si el proceso muere a mitad de camino, al reabrir la app
 * se ofrece "Continuar ruta anterior". Se borra al terminar o al llegar.
 */
interface ActiveRouteStore {
    fun save(route: Route)

    /** La última ruta guardada, o null si no hay (o es demasiado vieja o está dañada). */
    fun load(): Route?

    fun clear()
}
