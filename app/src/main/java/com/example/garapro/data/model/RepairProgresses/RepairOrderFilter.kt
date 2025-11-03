package com.example.garapro.data.model.RepairProgresses

enum class RoType {
    WalkIn, Scheduled, Breakdown
}

data class RepairOrderFilter(
    val statusId: Int? = null,
    val roType: RoType? = null,
    val paidStatus: String? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
    val pageNumber: Int = 1,
    val pageSize: Int = 10
)

data class FilterChipData(
    val id: String,
    val text: String,
    val type: FilterType,
    val isSelected: Boolean = false
)

enum class FilterType {
    STATUS, RO_TYPE, PAID_STATUS, DATE
}