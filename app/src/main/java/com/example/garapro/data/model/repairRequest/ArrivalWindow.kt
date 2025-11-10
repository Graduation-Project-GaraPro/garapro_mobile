package com.example.garapro.data.model.repairRequest

data class ArrivalWindow(
    val windowStart: String,   // e.g. "2025-11-07T08:00:00+07:00"
    val windowEnd: String,     // e.g. "2025-11-07T08:30:00+07:00"
    val capacity: Int,
    val approvedCount: Int,
    val remaining: Int,
    val isFull: Boolean
)