package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.model.VibrationEvent

interface VibrationEventsRepository {
    suspend fun record(event: VibrationEvent)
}
