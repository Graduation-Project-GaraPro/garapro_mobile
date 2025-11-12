package com.example.garapro.data.repository.RepairProgress

import com.example.garapro.data.model.RepairProgresses.OrderStatus
import com.example.garapro.data.model.RepairProgresses.RepairOrderFilter
import com.example.garapro.data.model.RepairProgresses.RepairOrderListItem
import com.example.garapro.data.model.RepairProgresses.RepairProgressDetail
import com.example.garapro.data.model.RepairProgresses.PagedResult
import com.example.garapro.data.model.payments.CreatePaymentRequest
import com.example.garapro.data.model.payments.CreatePaymentResponse
import com.example.garapro.data.model.payments.PaymentStatusDto
import com.example.garapro.data.remote.RepairProgressApiService
import com.example.garapro.data.remote.getMyRepairOrders

class RepairProgressRepository(private val apiService: RepairProgressApiService) {

    suspend fun getMyRepairOrders(filter: RepairOrderFilter): ApiResponse<PagedResult<RepairOrderListItem>> {
        return try {
            val response = apiService.getMyRepairOrders(filter)
            ApiResponse.Success(response)
        } catch (e: Exception) {
            ApiResponse.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getRepairOrderDetail(repairOrderId: String): ApiResponse<RepairProgressDetail> {
        return try {
            val response = apiService.getRepairProgressDetail(repairOrderId)
            ApiResponse.Success(response)
        } catch (e: Exception) {
            ApiResponse.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createPaymentLink(createPaymentRequest: CreatePaymentRequest): ApiResponse<CreatePaymentResponse> {
        return try {
            val response = apiService.createLink(createPaymentRequest)
            ApiResponse.Success(response)
        } catch (e: Exception) {
            ApiResponse.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getPaymentStatus(orderCode: Long): ApiResponse<PaymentStatusDto> {
        return try {
            val response = apiService.getStatus(orderCode)
            ApiResponse.Success(response)
        } catch (e: Exception) {
            ApiResponse.Error(e.message ?: "Unknown error")
        }
    }



    suspend fun getOrderStatuses(): ApiResponse<List<OrderStatus>> {
        return try {
            val response = apiService.getOrderStatuses()
            ApiResponse.Success(response)
        } catch (e: Exception) {
            ApiResponse.Error(e.message ?: "Unknown error")
        }
    }
    sealed class ApiResponse<T> {
        data class Success<T>(val data: T) : ApiResponse<T>()
        data class Error<T>(val message: String) : ApiResponse<T>()
        class Loading<T> : ApiResponse<T>()
    }
}