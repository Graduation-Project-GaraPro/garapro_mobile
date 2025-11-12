package com.example.garapro.data.remote

import com.example.garapro.data.model.UpdateDeviceIdRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PUT

interface AuthService {

    @PUT("users/device")
    suspend fun updateDeviceId(@Body request: UpdateDeviceIdRequest): Response<String>
}