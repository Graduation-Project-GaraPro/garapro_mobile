package com.example.garapro.data.model.repairRequest

data class Branch(
    val branchId: String,
    val branchName: String,
    val city: String,
    val district: String,
    val ward: String,
    val phoneNumber: String,
    val email: String,
    val description: String,
    val isActive: Boolean
)