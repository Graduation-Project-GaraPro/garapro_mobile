package com.example.garapro.data.model.repairRequest

data class Branch(
    val branchId: String,
    val branchName: String,
    val province: String,
    val commune: String,
    val street: String,
    val phoneNumber: String,
    val email: String,
    val description: String,
    val isActive: Boolean
)