package com.example.garapro.data.repository

import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.model.GoogleLoginRequest
import com.example.garapro.data.model.LoginRequest
import com.example.garapro.data.model.LoginResponse
import com.example.garapro.data.model.ResetPasswordRequest
import com.example.garapro.data.model.ResetPasswordResponse
import com.example.garapro.data.model.SignupRequest
import com.example.garapro.data.model.SignupResponse
import com.example.garapro.data.model.otpRequest
import com.example.garapro.data.model.otpResponse
import com.example.garapro.data.model.otpVerifyRequest
import com.example.garapro.data.remote.ApiService
import com.example.garapro.utils.Resource
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import kotlin.math.log

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {

    fun login(phoneNumber: String, password: String, rememberMe: Boolean): Flow<Resource<LoginResponse>> = flow {
        emit(Resource.Loading())

        try {
            val request = LoginRequest(phoneNumber, password, rememberMe)
            val response = apiService.login(request)

            if (response.isSuccessful && response.body() != null) {
                val loginResponse = response.body()!!

                if (loginResponse.token != null) {
                    // Lưu token
                    tokenManager.saveAccessToken(loginResponse.token)
                    loginResponse.roles.let {
                        tokenManager.saveUserRole(it.first())
                    }
                    loginResponse.userId?.let { id ->
                        tokenManager.saveUserId(id)
                    }
                    emit(Resource.Success(loginResponse))
                } else {
                    emit(Resource.Error(loginResponse.toString()))
                }
            } else {
                emit(Resource.Error("Đăng nhập thất bại: ${response.message()}"))
            }

        } catch (e: HttpException) {
            emit(Resource.Error("Lỗi server: ${e.localizedMessage}"))
        } catch (e: IOException) {
            emit(Resource.Error("Lỗi kết nối: Kiểm tra internet của bạn ${e.localizedMessage}"))

        } catch (e: Exception) {
            emit(Resource.Error("Lỗi không xác định: ${e.localizedMessage}"))
        }
    }

    suspend fun loginWithGoogle(idToken: String): Result<LoginResponse> {
        return try {
            val response = apiService.googleLogin(GoogleLoginRequest(idToken = idToken))

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {

                    // lưu token bằng TokenManager
                    tokenManager.saveAccessToken(body.token ?: "")

                    Result.success(body)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("Google login failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    fun signup(signupRequest: SignupRequest): Flow<Resource<SignupResponse>> = flow {
        emit(Resource.Loading())

        try {
            val response = apiService.signup(signupRequest)

            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                val errorBody = response.errorBody()?.string()

                if (!errorBody.isNullOrEmpty()) {
                    val gson = Gson()
                    val apiError = gson.fromJson(errorBody, ApiErrorResponse::class.java)

                    // Lấy lỗi Password
                    val passwordErrors = apiError.errors["Password"]
                    if (!passwordErrors.isNullOrEmpty()) {
                        emit(Resource.Error(passwordErrors.joinToString("\n")))
                    } else {
                        emit(Resource.Error("Đăng ký thất bại"))
                    }
                } else {
                    emit(Resource.Error("Lỗi server: ${response.message()}"))
                }
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "Lỗi không xác định"))
        }
    }


    fun sendOtp(phone: String, email: String?): Flow<Resource<otpResponse>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.sentOtp(otpRequest(phone, email))
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("Gửi OTP thất bại: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Lỗi: ${e.localizedMessage}"))
        }
    }

    fun verifyOtp(phone: String, otp: String): Flow<Resource<otpResponse>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.verifyOtp(otpVerifyRequest(phone, otp))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                    emit(Resource.Error(body.message))

            } else {
                emit(Resource.Error("Xác thực OTP thất bại: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Lỗi: ${e.localizedMessage}"))
        }
    }



    fun getToken(): Flow<String?> = tokenManager.getAccessToken()

    suspend fun logout() {
        tokenManager.clearTokens()
    }


}

data class ApiErrorResponse(
    val errors: Map<String, List<String>>
)