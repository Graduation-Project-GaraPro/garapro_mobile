package com.example.garapro.data.model.Vehicles

import com.google.gson.annotations.SerializedName

enum class VehicleBookingState(val value: Int) {
    @SerializedName("0") Available(0),
    @SerializedName("1") InGarage(1),
    @SerializedName("2") PickedUp(2)
}

data class VehicleSelectableDto(
    @SerializedName("vehicleId")
    val vehicleId: String,

    @SerializedName("licensePlate")
    val licensePlate: String?,

    @SerializedName("vin")
    val vin: String?,

    @SerializedName("year")
    val year: Int,

    @SerializedName("odometer")
    val odometer: Long?,

    @SerializedName("lastServiceDate")
    val lastServiceDate: String, // ISO string từ backend

    @SerializedName("nextServiceDate")
    val nextServiceDate: String?,

    @SerializedName("warrantyStatus")
    val warrantyStatus: String?,

    @SerializedName("createdAt")
    val createdAt: String,

    @SerializedName("updatedAt")
    val updatedAt: String?,

    @SerializedName("brandId")
    val brandId: String,

    @SerializedName("modelId")
    val modelId: String,

    @SerializedName("colorId")
    val colorId: String,

    @SerializedName("brandName")
    val brandName: String?,

    @SerializedName("modelName")
    val modelName: String?,

    @SerializedName("colorName")
    val colorName: String?,

    @SerializedName("hasActiveRepairRequest")
    val hasActiveRepairRequest: Boolean,

    @SerializedName("hasOpenRepairOrder")
    val hasOpenRepairOrder: Boolean,

    @SerializedName("hasArchivedRepairOrder")
    val hasArchivedRepairOrder: Boolean,

    @SerializedName("isSelectable")
    val isSelectable: Boolean,

    @SerializedName("state")
    val state: Int
)