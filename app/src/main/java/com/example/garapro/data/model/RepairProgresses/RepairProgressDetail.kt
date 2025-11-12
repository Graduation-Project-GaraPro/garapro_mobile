package com.example.garapro.data.model.RepairProgresses

data class RepairProgressDetail(
    val repairOrderId: String,
    val receiveDate: String,
    val roType: String,
    val estimatedCompletionDate: String?,
    val completionDate: String?,
    val cost: Double,
    val estimatedAmount: Double,
    val paidAmount: Double,
    val paidStatus: String,
    val note: String?,
    val vehicle: Vehicle,
    val orderStatus: OrderStatus,
    val jobs: List<Job>,
    val progressPercentage: Int,
    val progressStatus: String
)

data class Vehicle(
    val vehicleId: String,
    val licensePlate: String,
    val model: String,
    val brand: String,
    val year: Int
)

data class OrderStatus(
    val orderStatusId: Int,
    val statusName: String,
    val labels: List<Label>
)

data class Job(
    val jobId: String,
    val jobName: String,
    val status: String,
    val deadline: String?,
    val totalAmount: Double,
    val note: String?,
    val level: Int,
    val repair: Repair?,
    val parts: List<Part>,
    val technicians: List<Technician>
)

data class Repair(
    val repairId: String,
    val description: String,
    val startTime: String?,
    val endTime: String?,
    val actualTime: String?,
    val estimatedTime: String?,
    val notes: String?
)

data class Part(
    val partId: String,
    val name: String,
    val price: Double,
    val description: String
)

data class Technician(
    val technicianId: String,
    val fullName: String,
    val email: String,
    val phoneNumber: String
)