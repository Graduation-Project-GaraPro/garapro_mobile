package com.example.garapro.data.model.emergencies

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class RouteResponse(
    val geometry: JsonElement,
    @SerializedName("distanceMeters") val distanceMeters: Double?,
    @SerializedName("distanceKm") val distanceKm: Double?,
    @SerializedName("durationSeconds") val durationSeconds: Double?,
    @SerializedName("durationMinutes") val durationMinutes: Int?
)
