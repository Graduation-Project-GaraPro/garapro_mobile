package com.example.garapro.data.remote

import com.example.garapro.data.model.quotations.CustomerResponseRequest
import com.example.garapro.data.model.quotations.Quotation
import com.example.garapro.data.model.quotations.QuotationDetail
import com.example.garapro.data.model.quotations.QuotationResponse
import com.example.garapro.data.model.quotations.QuotationStatus
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface QuotationService {
    @GET("Quotations/user")
    suspend fun getQuotationsByUserId(
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 10,
        @Query("status") status: QuotationStatus? = null
    ): Response<QuotationResponse>


        @GET("Quotations/{id}/details")
        suspend fun getQuotationDetailById(@Path("id") id: String): Response<QuotationDetail>

    @PUT("Quotations/customer-response")
    suspend fun submitCustomerResponse(@Body responseDto: CustomerResponseRequest): Response<Unit>
}