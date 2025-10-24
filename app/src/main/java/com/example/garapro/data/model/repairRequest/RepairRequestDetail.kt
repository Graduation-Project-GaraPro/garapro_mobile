package com.example.garapro.data.model.repairRequest

import java.io.Serializable

data class RepairRequestDetail(
    val repairRequestID: String,
    val vehicleID: String,
    val userID: String,
    val branchId: String,
    val description: String,
    val requestDate: String,
    val status: Int,
    val estimatedCost: Double,
    val imageUrls: List<String>,
    val vehicle: VehicleDetail,
    val requestServices: List<RequestServiceDetail>
): Serializable

data class VehicleDetail(
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
    val brandName: String?,
    val modelName: String?,
    val colorName: String?
)

data class RequestServiceDetail(
    val serviceId: String,
    val parts: List<PartDetail>,
    val serviceName: String,
    val price: Double
)

data class PartDetail(
    val partId: String,
    val partName: String,
    val quantity: Int,
    val price: Double
)