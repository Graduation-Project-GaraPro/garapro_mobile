package com.example.garapro.data.model.RepairProgresses

data class PagedResult<T>(
    val items: List<T> = emptyList(),
    val totalCount: Int = 0,
    val pageNumber: Int = 1,
    val pageSize: Int = 10
) {
    val totalPages: Int
        get() = if (pageSize == 0) 0 else Math.ceil(totalCount / pageSize.toDouble()).toInt()
}