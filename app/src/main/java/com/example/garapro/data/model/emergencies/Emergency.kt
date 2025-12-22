package com.example.garapro.data.model.emergencies

import com.google.gson.annotations.SerializedName

data class Emergency(
    @SerializedName("emergencyRequestId", alternate = ["id", "emergencyId"])
    val id: String = "",

    @SerializedName("customerId", alternate = ["userId"])
    val userId: String = "",

    @SerializedName("latitude")
    val latitude: Double = 0.0,

    @SerializedName("longitude")
    val longitude: Double = 0.0,

    val timestamp: Long = 0L,

    @SerializedName("responseDeadline")
    val responseDeadline: String? = null,

    @SerializedName("status")
    val status: EmergencyStatus = EmergencyStatus.PENDING,

    @SerializedName("branchId", alternate = ["assignedGarageId", "garageId"])
    val assignedGarageId: String? = null,

    @SerializedName("assignedTechnicianName", alternate = ["technicianName"])
    val assignedTechnicianName: String? = null,

    // Handle typo from backend: "assginedTecinicianPhone"
    @SerializedName("assignedTechnicianPhone", alternate = ["assginedTecinicianPhone", "technicianPhone", "assginedTechnicianPhone"])
    val assignedTechnicianPhone: String? = null,

    @SerializedName("issueDescription")
    val issueDescription: String? = null,

    @SerializedName("emergencyType")
    val emergencyType: String? = null,

    @SerializedName("vehicleId")
    val vehicleId: String? = null
)

enum class EmergencyType {
    @SerializedName("OnSiteRepair") OnSiteRepair,
    @SerializedName("Towing") Towing
}

