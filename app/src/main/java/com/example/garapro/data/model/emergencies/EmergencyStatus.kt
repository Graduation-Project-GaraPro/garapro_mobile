package com.example.garapro.data.model.emergencies

import com.google.gson.annotations.SerializedName

enum class EmergencyStatus {
    @SerializedName("Pending")
    PENDING,

    @SerializedName("Accepted")
    ACCEPTED,

    @SerializedName("Assigned")
    ASSIGNED,

    @SerializedName("InProgress")
    IN_PROGRESS,

    @SerializedName("Towing")
    TOWING,

    @SerializedName("Completed")
    COMPLETED,

    @SerializedName("Cancelled", alternate = ["Canceled"])
    CANCELLED
}
