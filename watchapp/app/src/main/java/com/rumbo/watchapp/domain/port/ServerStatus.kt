package com.rumbo.watchapp.domain.port

import kotlinx.coroutines.flow.StateFlow

/** Estado del backend que se le muestra al usuario mientras espera. */
interface ServerStatus {
    /** true mientras una petición espera a que Render despierte el servidor (o lo reintenta). */
    val waking: StateFlow<Boolean>
}
