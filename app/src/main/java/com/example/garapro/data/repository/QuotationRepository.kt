package com.example.garapro.data.repository

import android.util.Log
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.model.quotations.CustomerResponseRequest
import com.example.garapro.data.model.quotations.Quotation
import com.example.garapro.data.model.quotations.QuotationDetail
import com.example.garapro.data.model.quotations.QuotationResponse
import com.example.garapro.data.remote.QuotationService
import com.example.garapro.data.model.quotations.QuotationStatus
import com.example.garapro.data.remote.RetrofitInstance.quotationService

class QuotationRepository(
    private val quotationService: QuotationService
) {
    suspend fun getQuotations(
        pageNumber: Int = 1,
        pageSize: Int = 10,
        status: QuotationStatus? = null
    ): Result<QuotationResponse> {
        return try {
            val response = quotationService.getQuotationsByUserId(pageNumber, pageSize, status)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch quotations: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    }

    suspend fun getQuotationDetailById(id: String): Result<QuotationDetail> {
        return try {
            val response = quotationService.getQuotationDetailById(id)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch quotation detail: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitCustomerResponse(request: CustomerResponseRequest): Result<Unit> {
        return try {
            val response = quotationService.submitCustomerResponse(request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to submit response: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}