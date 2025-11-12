package com.example.garapro.data.model.repairRequest

data class ServiceCategory(
    val serviceCategoryId: String,
    val categoryName: String,
    val serviceTypeId: String,
    val parentServiceCategoryId: String?,
    val description: String?,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String?,
    val services: List<Service>,
    val childCategories: List<ServiceCategory>?
)

data class Service(
    val serviceId: String,
    val serviceCategoryId: String,
    val serviceName: String,
    val description: String,
    val price: Double,
    val discountedPrice: Double,
    val estimatedDuration: Int,
    val isActive: Boolean,
    val isAdvanced: Boolean,
    val createdAt: String,
    val updatedAt: String?,
    val serviceCategory: ServiceCategoryInfo,
    val partCategories: List<PartCategory>
)

data class ServiceCategoryInfo(
    val serviceCategoryId: String,
    val categoryName: String,
    val serviceTypeId: String,
    val parentServiceCategoryId: String?,
    val description: String?,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String?
)

data class PartCategory(
    val partCategoryId: String,
    val categoryName: String,
    val parts: List<Part>
)

data class Part(
    val partId: String,
    val name: String,
    val price: Double,
    val stock: Int
)