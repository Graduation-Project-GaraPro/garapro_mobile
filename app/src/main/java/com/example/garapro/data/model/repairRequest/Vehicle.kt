package com.example.garapro.data.model.repairRequest

data class Vehicle(
    val vehicleID: String,
    val brandID: String,
    val userID: String,
    val modelID: String,
    val colorID: String,
    val licensePlate: String,
    val vin: String,
    val year: Int,
    val odometer: Int,
    val lastServiceDate: String?,
    val nextServiceDate: String?,
    val warrantyStatus: String,
    val brandName: String,
    val modelName: String,
    val colorName: String
)