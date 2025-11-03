package com.example.garapro.data.model.repairRequest

// RepairRequest.kt
data class RepairRequest(
    val repairRequestID: String,
    val vehicleID: String,
    val userID: String,
    val description: String,
    val branchId: String,
    val requestDate: String,
    val completedDate: String?,
    val status: Int,
    val createdAt: String,
    val updatedAt: String?,
    val estimatedCost: Double
)

data class PagedRepairRequestResponse(
    val totalCount: Int,
    val pageNumber: Int,
    val pageSize: Int,
    val totalPages: Int,
    val data: List<RepairRequest>
)

enum class RepairRequestStatus(val value: Int) {
    Pending(0),
    Accept(1),
    Arrived(2),
    Cancelled(3)
}