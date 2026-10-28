package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.domain.port.SavedPlacesRepository
import org.json.JSONObject

/** Lugares guardados con GET/POST /saved-places y DELETE /saved-places/{id}. */
object SavedPlacesApiClient : SavedPlacesRepository {

    override suspend fun list(): List<SavedPlace> {
        val body = backendRequest("GET", "/saved-places").requireOk()
        return parseJsonArray(body, ::parsePlace)
    }

    override suspend fun add(name: String, lat: Double, lng: Double): SavedPlace {
        val request = JSONObject().put("name", name).put("lat", lat).put("lng", lng)
        val body = backendPostJson("/saved-places", request).requireOk()
        return parseJson(body, ::parsePlace)
    }

    override suspend fun delete(id: Long) {
        backendRequest("DELETE", "/saved-places/$id").requireOk()
    }

    private fun parsePlace(json: JSONObject) = SavedPlace(
        id = json.getLong("id"),
        name = json.getString("name"),
        lat = json.getDouble("lat"),
        lng = json.getDouble("lng"),
    )
}
