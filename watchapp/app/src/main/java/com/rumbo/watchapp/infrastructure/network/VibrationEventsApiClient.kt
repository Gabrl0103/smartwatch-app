package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.model.VibrationEvent
import com.rumbo.watchapp.domain.port.VibrationEventsRepository
import org.json.JSONObject

/** Anota cada vibración emitida con POST /vibration-events. */
object VibrationEventsApiClient : VibrationEventsRepository {

    override suspend fun record(event: VibrationEvent) {
        val request = JSONObject().put("pattern", event.pattern)
        event.routeId?.let { request.put("route_id", it) }
        event.stepOrder?.let { request.put("step_order", it) }
        backendPostJson("/vibration-events", request).requireOk()
    }
}
