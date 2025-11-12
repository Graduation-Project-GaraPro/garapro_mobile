package com.example.garapro.data.model.emergencies

import com.google.gson.annotations.SerializedName


class AutoCompleteResponse {
    @SerializedName("predictions")
    val predictions: MutableList<Prediction?>? = null

    @SerializedName("status")
    private val status: String? = null

    class Prediction {
        @SerializedName("description")
        val description: String? = null

        @SerializedName("place_id")
        val placeId: String? = null

        @SerializedName("reference")
        private val reference: String? = null
    }
}