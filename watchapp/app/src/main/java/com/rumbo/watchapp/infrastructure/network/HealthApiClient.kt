package com.rumbo.watchapp.infrastructure.network

/** GET /health: sirve para despertar el servidor de Render apenas se abre la app. */
object HealthApiClient {

    suspend fun ping() {
        backendRequest("GET", "/health", retry = false).requireOk()
    }
}
