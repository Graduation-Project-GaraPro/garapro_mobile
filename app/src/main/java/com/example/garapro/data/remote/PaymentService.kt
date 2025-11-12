package com.example.garapro.data.remote

import com.example.garapro.data.model.payments.CreatePaymentRequest
import com.example.garapro.data.model.payments.CreatePaymentResponse
import com.example.garapro.data.model.payments.PaymentStatusDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface PaymentService {

    @POST("Payments/create-link")
    suspend fun createLink(@Body body: CreatePaymentRequest): CreatePaymentResponse

    @GET("Payments/status/{orderCode}")
    suspend fun getStatus(@Path("orderCode") orderCode: Long): PaymentStatusDto

}