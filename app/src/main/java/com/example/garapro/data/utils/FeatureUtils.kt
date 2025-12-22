package com.example.garapro.data.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject

object FeatureUtils {
    fun createPoint(lat: Double, lng: Double, properties: JsonObject? = null): String {
        return JsonObject().apply {
            addProperty("type", "Feature")
            add("geometry", JsonObject().apply {
                addProperty("type", "Point")
                add("coordinates", JsonArray().apply {
                    add(lng)
                    add(lat)
                })
            })
            add("properties", properties ?: JsonObject())
        }.toString()
    }
}