package com.example.garapro.data.model.payments

data class CreatePaymentRequest(
    val repairOrderId: String,
    val userId: String = "5a434214-c204-4437-a2e4-bc32fdd57670",
    val amount: Int, // hoặc Int/Double theo BE
    val description: String,
    val returnUrl: String? = null,
    val cancelUrl: String? = null,
    val orderCode: Long? = null
)

data class CreatePaymentResponse(
    val paymentId: Int,
    val orderCode: Long,
    val checkoutUrl: String
)

data class PaymentStatusDto(
    val orderCode: Long,
    val status: PaymentStatus,
    val providerCode: String?,
    val providerDesc: String?
)
enum class PaymentStatus {
    Paid,
    Unpaid,
    Cancelled,
    Failed
}
