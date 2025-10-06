package com.example.garapro.data.remote

import com.example.garapro.data.local.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenManager: TokenManager,
    private val apiService: ApiService
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Bỏ qua các endpoint không cần token
        val url = originalRequest.url.toString()
        if (url.contains("/auth/login") ||
            url.contains("/auth/signup") ||
            url.contains("/auth/refresh")) {
            return chain.proceed(originalRequest)
        }

        // Lấy access token
        val accessToken = runBlocking {
            tokenManager.getAccessTokenSync()
        }

        // Thêm access token vào header
        val requestWithToken = if (accessToken != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            originalRequest
        }

        // Thực hiện request
        var response = chain.proceed(requestWithToken)

        // Nếu response 401 (Unauthorized), thử refresh token
        if (response.code == 401) {
            response.close()

            val newAccessToken = runBlocking {
                refreshAccessToken()
            }

            if (newAccessToken != null) {
                // Retry request với token mới
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
                response = chain.proceed(newRequest)
            }
        }

        return response
    }

    private suspend fun refreshAccessToken(): String? {
        return try {
            // Gọi API refresh - cookie sẽ tự động được gửi kèm
            val response = apiService.refreshToken()

            if (response.isSuccessful && response.body() != null) {
                val refreshResponse = response.body()!!

                if (refreshResponse.token != null) {
                    // Lưu access token mới
                    // Refresh token mới (nếu có) đã được lưu trong cookie tự động
                    tokenManager.saveAccessToken(refreshResponse.token)
                    return refreshResponse.token
                }
            }

            // Nếu refresh thất bại, xóa token
            tokenManager.clearTokens()
            null

        } catch (e: Exception) {
            tokenManager.clearTokens()
            null
        }
    }
}