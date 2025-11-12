package com.example.garapro.data.model.repairRequest

import okhttp3.MultipartBody

data class CreateRepairRequest(
    val branchId: String,
    val vehicleID: String,
    val description: String,
    val requestDate: String,
    val images: List<MultipartBody.Part>, // Đổi từ imageUrls sang images dạng Multipart
    val services: List<ServiceRequest>
)

data class ServiceRequest(
    val serviceId: String,
    val parts: List<PartRequest>
)

data class PartRequest(
    val partId: String
)