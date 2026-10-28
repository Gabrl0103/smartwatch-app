package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.model.SavedPlace

interface SavedPlacesRepository {
    suspend fun list(): List<SavedPlace>

    suspend fun add(name: String, lat: Double, lng: Double): SavedPlace

    suspend fun delete(id: Long)
}
