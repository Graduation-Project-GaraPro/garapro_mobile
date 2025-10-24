package com.example.garapro.data.remote

import com.example.garapro.data.local.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

interface TokenExpiredListener {
    fun onTokenExpired()
}

class AuthInterceptor(
    private val tokenManager: TokenManager,
    private val apiService: ApiService,
    private val tokenExpiredListener: TokenExpiredListener? = null
) : Interceptor {



    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()

        if (url.contains("/auth/login") ||
            url.contains("/auth/signup") ||
            url.contains("/auth/refresh")) {
            return chain.proceed(originalRequest)
        }

        val accessToken = runBlocking { tokenManager.getAccessTokenSync() }

        val requestWithToken = accessToken?.let {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $it")
                .build()
        } ?: originalRequest

        var response = chain.proceed(requestWithToken)

        if (response.code == 401) {
            response.close()
            val newAccessToken = runBlocking { refreshAccessToken() }

            if (newAccessToken != null) {
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
                response = chain.proceed(newRequest)
            } else {
                // ⚠️ Refresh token hết hạn
                tokenExpiredListener?.onTokenExpired()
            }
        }

        return response
    }

    private suspend fun refreshAccessToken(): String? {
        return try {
            val response = apiService.refreshToken()
            if (response.isSuccessful && response.body()?.token != null) {
                val newToken = response.body()!!.token
                tokenManager.saveAccessToken(newToken!!)
                newToken
            } else {
                tokenManager.clearTokens()
                null
            }
        } catch (e: Exception) {
            tokenManager.clearTokens()
            null
        }
    }
}