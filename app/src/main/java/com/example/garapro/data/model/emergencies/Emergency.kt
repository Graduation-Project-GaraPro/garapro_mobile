package com.example.garapro.data.model.emergencies

data class Emergency(
    val id: String = "",
    val userId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L,
    val status: EmergencyStatus = EmergencyStatus.PENDING,
    val assignedGarageId: String? = null
)

