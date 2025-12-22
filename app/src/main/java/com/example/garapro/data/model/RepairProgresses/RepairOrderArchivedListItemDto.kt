package com.example.garapro.data.model.RepairProgresses


import android.os.Parcelable
import kotlinx.parcelize.Parcelize
data class RepairOrderArchivedListItem(
    val repairOrderId: String,
    val receiveDate: String,
    val completionDate: String?,
    val archivedAt: String?,
    val licensePlate: String,
    val branchName: String,
    val brandName: String,
    val modelName: String,
    val cost: Double,
    val paidAmount: Double,
    val statusId: Int
)

data class RepairOrderArchivedDetail(
    val repairOrderId: String,
    val receiveDate: String,
    val completionDate: String?,
    val archivedAt: String?,
    val isArchived: Boolean,

    val licensePlate: String,
    val branchName: String,
    val brandName: String,

    val modelName: String,

    val cost: Double,
    val estimatedAmount: Double,
    val paidAmount: Double,
    val paidStatus: String, // hoặc PaidStatus enum bên server map sang string
    val carPickupStatus: CarPickupStatus = CarPickupStatus.None ,
    val note: String?,

    val jobs: List<ArchivedJob>,
    val feedBacks: Feedback?
)
enum class CarPickupStatus {
    None,
    PickedUp,
    NotPickedUp
}
@Parcelize
data class ArchivedJob(
    val jobId: String,
    val jobName: String,
    val status: String,
    val deadline: String?,
    val servicePrice: Double,
    val discountValue: Double?,
    val totalAmount: Double,
    val repair: ArchivedRepair?,
    val technicians: List<ArchivedTechnician>,
    val parts: List<ArchivedJobPart>
) : Parcelable

@Parcelize
data class ArchivedRepair(
    val startTime: String?,
    val endTime: String?,
    val notes: String?
) : Parcelable

@Parcelize
data class ArchivedTechnician(
    val technicianId: String,
    val fullName: String,
    val quality: Float,
    val speed: Float,
    val efficiency: Float,
    val score: Float
) : Parcelable

@Parcelize
data class ArchivedJobPart(
    val jobPartId: String,
    val partId: String,
    val partName: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double,
    val warrantyMonths: Int?,
    val warrantyStartAt: String?,
    val warrantyEndAt: String?
) : Parcelable
data class RepairOrderArchivedFilter(
    val fromDate: String? = null,
    val toDate: String? = null,
    val roType: RoType? = null,
    val paidStatus: String? = null,
    val pageNumber: Int = 1,
    val pageSize: Int = 10
)

