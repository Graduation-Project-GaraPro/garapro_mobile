package com.example.garapro.ui.appointments.model

data class Appointment(
    val id: Int,
    val garageName: String,
    val serviceName: String,
    val dateTime: String,
    val carPlate: String,
    val status: String
)
