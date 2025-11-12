package com.example.garapro.data.remote

import com.example.garapro.data.local.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import com.example.garapro.utils.Constants
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {

    private var isInitialized = false
    private lateinit var tokenManager: TokenManager
    private lateinit var tokenExpiredListener: TokenExpiredListener

    fun initialize(
        tokenManager: TokenManager,
        tokenExpiredListener: TokenExpiredListener
    ) {
        this.tokenManager = tokenManager
        this.tokenExpiredListener = tokenExpiredListener
        this.isInitialized = true
    }

    // Tạo AuthService riêng biệt không dùng interceptor (cho refresh token)
    private val authService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient.Builder().build())
            .build()
            .create(ApiService::class.java)
    }

    private fun getRetrofit(): Retrofit {
        if (!isInitialized) {
            throw IllegalStateException("RetrofitInstance chưa được khởi tạo. Gọi initialize() trước.")
        }

        return Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(createOkHttpClient())
            .build()
    }

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(createAuthInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun createAuthInterceptor(): AuthInterceptor {
        return AuthInterceptor(tokenManager, authService, tokenExpiredListener)
    }

    val quotationService: QuotationService by lazy {
        getRetrofit().create(QuotationService::class.java)
    }

    val paymentService: PaymentService by lazy {
        getRetrofit().create(PaymentService::class.java)
    }

    val UserService: AuthService by lazy {
        getRetrofit().create(AuthService::class.java)
    }

    val RepairProgressService: RepairProgressApiService by lazy {
        getRetrofit().create(RepairProgressApiService::class.java)
    }

    // Các service khác...
    val bookingService: BookingService by lazy {
        getRetrofit().create(BookingService::class.java)
    }
}