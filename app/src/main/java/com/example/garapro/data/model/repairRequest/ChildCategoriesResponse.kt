package com.example.garapro.data.model.repairRequest

data class ChildCategoriesResponse(
    val totalCount: Int,
    val pageNumber: Int,
    val pageSize: Int,
    val data: List<ServiceCategory>
)