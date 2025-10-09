package com.example.garapro.data.repository

import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.model.ImageResponse
import com.example.garapro.data.model.User

import com.example.garapro.data.remote.ApiService
import com.example.garapro.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MultipartBody
import retrofit2.HttpException
import java.io.IOException

class UserRepository(
    private val apiService: ApiService
) {

    fun getMe(): Flow<Resource<User>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getMe()
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("Lỗi: ${response.message()}"))
            }
        } catch (e: IOException) {
            emit(Resource.Error("Lỗi mạng"))
        } catch (e: HttpException) {
            emit(Resource.Error("Lỗi server: ${e.localizedMessage}"))
        }
    }

    suspend fun updateUser(user: User): Resource<User> {
        return try {
            val response = apiService.updateProfile(user)
            if (response.isSuccessful && response.body() != null)
                Resource.Success(response.body()!!)
            else
                Resource.Error("Lỗi cập nhật: ${response.message()}")
        } catch (e: Exception) {
            Resource.Error("Lỗi: ${e.localizedMessage}")
        }
    }

    suspend fun uploadImage(filePart: MultipartBody.Part): Resource<ImageResponse> {
        return try {
            val response = apiService.uploadImage(filePart)
            if (response.isSuccessful && response.body() != null)
                Resource.Success(response.body()!!)
            else
                Resource.Error("Upload thất bại: ${response.message()}")
        } catch (e: Exception) {
            Resource.Error("Lỗi: ${e.localizedMessage}")
        }
    }
}
