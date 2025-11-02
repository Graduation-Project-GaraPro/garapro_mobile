package com.example.garapro.data.model.quotations

data class QuotationDetail(
    val quotationId: String,
    val inspectionId: String?,
    val repairOrderId: String?,
    val userId: String,
    val vehicleId: String,
    val createdAt: String,
    val sentToCustomerAt: String?,
    val customerResponseAt: String?,
    val status: QuotationStatus,
    val totalAmount: Double,
    val discountAmount: Double,
    val note: String?,
    val customerNote: String?,
    val expiresAt: String?,
    val customerName: String,
    val vehicleInfo: String?,
    val quotationServices: List<QuotationServiceDetail>,
    val inspection: Inspection?,
    val repairOrder: RepairOrder?
)

data class QuotationServiceDetail(
    val quotationServiceId: String,
    val quotationId: String,
    val serviceId: String,
    var isSelected: Boolean,
    val isRequired: Boolean, // 🔥 THÊM: Service bắt buộc không được bỏ
    val price: Double,
    val quantity: Int,
    val totalPrice: Double,
    val createdAt: String,
    val serviceName: String,
    val serviceDescription: String?,
    val partCategories: List<PartCategory>
)

data class PartCategory(
    val partCategoryId: String,
    val partCategoryName: String,
    val parts: List<QuotationServicePart>,
    val isAdvanced: Boolean
)

data class SelectedService(
    val quotationServiceId: String
)

data class SelectedServicePart(
    val quotationServicePartId: String
)