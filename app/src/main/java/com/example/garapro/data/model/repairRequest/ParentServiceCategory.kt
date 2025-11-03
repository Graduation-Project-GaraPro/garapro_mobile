package com.example.garapro.data.model.repairRequest

data class ParentServiceCategory(
    val serviceCategoryId: String,
    val categoryName: String,
    val parentServiceCategoryId: String?,
    val description: String?,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String?,
    val services: List<Service>,
    val childCategories: List<ChildServiceCategory>?
)
data class ChildServiceCategory(
    val serviceCategoryId: String,
    val categoryName: String,
    val parentServiceCategoryId: String?,
    val description: String?,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String?,
    val services: List<Service>?,
    val childCategories: List<ChildServiceCategory>?
)