package com.example.garapro.data.remote

import android.content.Context
import com.example.garapro.data.local.PersistentCookieJar
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.model.LoginRequest
import com.example.garapro.data.model.LoginResponse
import com.example.garapro.data.model.RefreshTokenResponse
import com.example.garapro.data.model.SignupRequest
import com.example.garapro.data.model.SignupResponse
import com.example.garapro.data.model.User

import com.example.garapro.data.model.otpRequest
import com.example.garapro.data.model.otpResponse
import com.example.garapro.data.model.otpVerifyRequest
import com.example.garapro.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("users/me")
    suspend fun getMe(): Response<User>

    @POST("auth/refresh-token")
    suspend fun refreshToken(): Response<RefreshTokenResponse>

    @POST("auth/send-otp")
    suspend fun sentOtp(@Body request: otpRequest): Response<otpResponse>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body request: otpVerifyRequest): Response<otpResponse>

    @POST("auth/complete-registration")
    suspend fun signup(@Body request: SignupRequest): Response<SignupResponse>


    object ApiClient {
        private var apiService: ApiService? = null

        fun getApiService(context: Context, tokenManager: TokenManager): ApiService {
            if (apiService == null) {
                apiService = ApiService.create(context, tokenManager)
            }
            return apiService!!
        }

        fun clearCookies() {
            ApiService.clearCookies()
        }
    }


    companion object {
        private var instance: ApiService? = null
        private var cookieJar: PersistentCookieJar? = null

        fun create(context: Context, tokenManager: TokenManager? = null): ApiService {
            // Khởi tạo CookieJar nếu chưa có
            if (cookieJar == null) {
                cookieJar = PersistentCookieJar(context)
            }

            // Nếu đã có instance và không cần interceptor, trả về instance
            if (instance != null && tokenManager == null) {
                return instance!!
            }

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val clientBuilder = OkHttpClient.Builder()
                .cookieJar(cookieJar!!) // Thêm CookieJar để tự động lưu/gửi cookies
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)

            // Thêm AuthInterceptor nếu có tokenManager
            if (tokenManager != null) {
                val tempApiService = createTempApiService(context)
                clientBuilder.addInterceptor(
                    AuthInterceptor(tokenManager, tempApiService)
                )
            }

            val client = clientBuilder.build()

            val apiService = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)

            if (tokenManager != null) {
                instance = apiService
            }

            return apiService
        }

        // Tạo ApiService tạm cho AuthInterceptor (không có interceptor)
        private fun createTempApiService(context: Context): ApiService {
            val client = OkHttpClient.Builder()
                .cookieJar(cookieJar!!) // Vẫn cần CookieJar cho refresh token
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }

        // Hàm clear cookies khi logout
        fun clearCookies() {
            cookieJar?.clearCookies()
        }
    }
}