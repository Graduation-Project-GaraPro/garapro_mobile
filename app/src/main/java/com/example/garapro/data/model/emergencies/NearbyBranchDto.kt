package com.example.garapro.data.model.emergencies

data class NearbyBranchDto(
    val branchId: String,
    val branchName: String,
    val phoneNumber: String,
    val address: String,
    val distanceKm: Double,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)