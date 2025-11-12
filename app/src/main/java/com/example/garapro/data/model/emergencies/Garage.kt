package com.example.garapro.data.model.emergencies

data class Garage(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val phone: String = "",
    val isAvailable: Boolean = true,
    val price: Double = 0.0,
    val rating: Float = 0f,
    val distance: Double = 0.0 // calculated field
)