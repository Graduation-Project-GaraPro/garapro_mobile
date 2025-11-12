package com.example.garapro.data.remote

import com.example.garapro.data.model.RepairProgresses.OrderStatus
import com.example.garapro.data.model.RepairProgresses.PagedResult
import com.example.garapro.data.model.RepairProgresses.RepairOrderFilter
import com.example.garapro.data.model.RepairProgresses.RepairOrderListItem
import com.example.garapro.data.model.RepairProgresses.RepairProgressDetail
import com.example.garapro.data.model.RepairProgresses.RoType
import com.example.garapro.data.model.payments.CreatePaymentRequest
import com.example.garapro.data.model.payments.CreatePaymentResponse
import com.example.garapro.data.model.payments.PaymentStatusDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RepairProgressApiService {

    @GET("RepairProgress/my-orders")
    suspend fun getMyRepairOrders(
        @Query("statusId") statusId: Int? = null,
        @Query("roType") roType: RoType? = null,
        @Query("paidStatus") paidStatus: String? = null,
        @Query("fromDate") fromDate: String? = null,
        @Query("toDate") toDate: String? = null,
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 10
    ): PagedResult<RepairOrderListItem>

    @GET("RepairProgress/{repairOrderId}/progress")
    suspend fun getRepairProgressDetail(
        @Path("repairOrderId") repairOrderId: String
    ): RepairProgressDetail
    @POST("Payments/create-link")
    suspend fun createLink(@Body body: CreatePaymentRequest): CreatePaymentResponse

    @GET("Payments/status/{orderCode}")
    suspend fun getStatus(@Path("orderCode") orderCode: Long): PaymentStatusDto
    @GET("OrderStatus")
    suspend fun getOrderStatuses(): List<OrderStatus>
}

// Extension function để hỗ trợ filter object
suspend fun RepairProgressApiService.getMyRepairOrders(filter: RepairOrderFilter): PagedResult<RepairOrderListItem> {
    return getMyRepairOrders(
        statusId = filter.statusId,
        roType = filter.roType,
        paidStatus = filter.paidStatus,
        fromDate = filter.fromDate,
        toDate = filter.toDate,
        pageNumber = filter.pageNumber,
        pageSize = filter.pageSize
    )
}