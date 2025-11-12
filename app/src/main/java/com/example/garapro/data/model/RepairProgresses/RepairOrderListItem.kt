package com.example.garapro.data.model.RepairProgresses

data class RepairOrderListItem(
    val repairOrderId: String,
    val receiveDate: String,
    val roType: String,
    val estimatedCompletionDate: String?,
    val completionDate: String?,
    val cost: Double,
    val paidStatus: String,
    val vehicleLicensePlate: String,
    val vehicleModel: String,
    val statusName: String,
    val labels: List<Label>,
    val progressPercentage: Int,
    val progressStatus: String
)

data class Label(
    val labelId: String,
    val labelName: String,
    val description: String,
    val colorName: String,
    val hexCode: String
)