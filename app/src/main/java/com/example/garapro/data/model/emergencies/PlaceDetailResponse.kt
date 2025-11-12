package com.example.garapro.data.model.emergencies

import com.google.gson.annotations.SerializedName


class PlaceDetailResponse {
    @SerializedName("result")
    val result: Result? = null

    @SerializedName("status")
    private val status: String? = null

    class Result {
        @SerializedName("formatted_address")
        val formattedAddress: String? = null

        @SerializedName("geometry")
        val geometry: Geometry? = null

        @SerializedName("name")
        val name: String? = null

        @SerializedName("place_id")
        private val placeId: String? = null
    }

    class Geometry {
        @SerializedName("location")
        val location: Location? = null
    }

    class Location {
        @SerializedName("lat")
        val lat: Double = 0.0

        @SerializedName("lng")
        val lng: Double = 0.0
    }
}