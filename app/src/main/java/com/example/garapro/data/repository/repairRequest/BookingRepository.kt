package com.example.garapro.data.repository.repairRequest

import android.content.Context
import android.util.Log
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.model.NetworkResult
import com.example.garapro.data.model.Vehicles.VehicleSelectableDto
import com.example.garapro.data.model.repairRequest.ArrivalWindow
import com.example.garapro.data.model.repairRequest.Branch
import com.example.garapro.data.model.repairRequest.ChildCategoriesResponse
import com.example.garapro.data.model.repairRequest.CreateRepairRequest
import com.example.garapro.data.model.repairRequest.ParentServiceCategory
import com.example.garapro.data.model.repairRequest.RepairRequest
import com.example.garapro.data.model.repairRequest.RepairRequestDetail
import com.example.garapro.data.model.repairRequest.ServiceCategory
import com.example.garapro.data.model.repairRequest.Vehicle
import com.example.garapro.data.remote.RetrofitInstance
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject

class BookingRepository(
    private val context: Context,
    private val tokenManager: TokenManager
) {

    // 🔹 Parent Service Categories
    suspend fun getParentServiceCategories(vehicleId: String, branchId: String): List<ParentServiceCategory> {
        return try {
            val response = RetrofitInstance.bookingService.getParentServiceCategories(vehicleId,branchId)
            if (response.isSuccessful) {
                Log.w("BookingRepository", "getParentServiceCategories  ${response.body()}")
                Log.w("BookingRepository", vehicleId)

                response.body() ?: emptyList()
            } else {
                Log.w("BookingRepository", "getParentServiceCategories failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("BookingRepository", "Error getParentServiceCategories: ${e.message}", e)
            emptyList()
        }
    }

    // 🔹 Arrival Availability
    suspend fun getArrivalAvailability(branchId: String, date: String): List<ArrivalWindow> {
        return try {
            val response = RetrofitInstance.bookingService.getArrivalAvailability(branchId, date)
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                Log.w("BookingRepository", "getArrivalAvailability failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("BookingRepository", "Error getArrivalAvailability: ${e.message}", e)
            emptyList()
        }
    }

    // 🔹 Repair Requests Paged
    suspend fun getRepairRequestsPaged(
        pageNumber: Int = 1,
        pageSize: Int = 10,
        vehicleId: String? = null,
        status: Int? = null,
        branchId: String? = null
    ): List<RepairRequest> {
        return try {
            val response = RetrofitInstance.bookingService.getRepairRequestsPaged(
                pageNumber = pageNumber,
                pageSize = pageSize,
                vehicleId = vehicleId,
                status = status,
                branchId = branchId
            )

            if (response.isSuccessful) {
                response.body()?.data ?: emptyList()
            } else {
                Log.w("BookingRepository", "getRepairRequestsPaged failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("BookingRepository", "Error getRepairRequestsPaged: ${e.message}", e)
            emptyList()
        }
    }

    // 🔹 Child Service Categories (có phân trang + filter)
    suspend fun getChildServiceCategories(
        parentId: String,
        pageNumber: Int = 1,
        pageSize: Int = 10,
        childServiceCategoryId: String? = null,
        searchTerm: String? = null,
        branchId: String? = null,
        vehicleId: String? = null
    ): ChildCategoriesResponse {
        return try {
            val response = RetrofitInstance.bookingService.getChildServiceCategories(
                parentId = parentId,
                pageNumber = pageNumber,
                pageSize = pageSize,
                childServiceCategoryId = childServiceCategoryId,
                searchTerm = searchTerm,
                branchId = branchId,
                vehicleId = vehicleId
            )
            if (response.isSuccessful) {
                Log.w("BookingRepository", "getChildServiceCategories : ${response.body()}")

                response.body() ?: ChildCategoriesResponse(
                    totalCount = 0,
                    pageNumber = pageNumber,
                    pageSize = pageSize,
                    data = emptyList()
                )
            } else {
                Log.w("BookingRepository", "getChildServiceCategories failed: ${response.code()}")
                ChildCategoriesResponse(
                    totalCount = 0,
                    pageNumber = pageNumber,
                    pageSize = pageSize,
                    data = emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e("BookingRepository", "Error getChildServiceCategories: ${e.message}", e)
            ChildCategoriesResponse(
                totalCount = 0,
                pageNumber = pageNumber,
                pageSize = pageSize,
                data = emptyList()
            )
        }
    }

    // 🔹 Repair Request Detail
    suspend fun getRepairRequestDetail(id: String): RepairRequestDetail? {
        return try {
            val response = RetrofitInstance.bookingService.getRepairRequestDetail(id)
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.w("BookingRepository", "getRepairRequestDetail failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e("BookingRepository", "Error getRepairRequestDetail: ${e.message}", e)
            null
        }
    }

    suspend fun getVehicles(): List<Vehicle> {
        return runCatching {
            RetrofitInstance.bookingService.getVehicles()
        }.onFailure { e ->
            Log.e("BookingRepository", "Error getVehicles", e)
        }.getOrNull()
            ?.takeIf { it.isSuccessful }
            ?.body()
            ?: emptyList()
    }

    // 🔹 Vehicles
    suspend fun getVehiclesSelectable(): List<VehicleSelectableDto> {
        return try {
            val response = RetrofitInstance.bookingService.getVehiclesSelectable()
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                Log.w("BookingRepository", "getVehicles failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("BookingRepository", "Error getVehicles: ${e.message}", e)
            emptyList()
        }
    }

    // 🔹 Branches
    suspend fun getBranches(): List<Branch> {
        return try {
            val response = RetrofitInstance.bookingService.getBranches()
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                handleApiError(response)
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("BookingRepository", "Error getBranches: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun cancelRepairRequest(id: String): NetworkResult<Unit> {
        return try {
            val response = RetrofitInstance.bookingService.cancelRepairRequest(id)

            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                val errorMessage = parseApiError(response.errorBody())
                NetworkResult.Error(errorMessage, response.code())
            }
        } catch (e: Exception) {
            Log.e("BookingRepository", "Error cancelRepairRequest: ${e.message}", e)
            NetworkResult.Error(e.message ?: "Something went wrong.")
        }
    }

    // 🔹 Service Categories
    suspend fun getServiceCategories(): List<ServiceCategory> {
        return try {
            val response = RetrofitInstance.bookingService.getServiceCategories()
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                handleApiError(response)
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("BookingRepository", "Error getServiceCategories: ${e.message}", e)
            emptyList()
        }
    }

    // 🔹 Submit Repair Request (giữ nguyên, không dùng mock)
    suspend fun submitRepairRequest(request: CreateRepairRequest): NetworkResult<Unit> {
        return try {
            val dtoJson = Gson().toJson(request.copy(images = emptyList()))
            val dtoJsonBody = dtoJson.toRequestBody("text/plain".toMediaTypeOrNull())
            val imageParts = request.images

            val response = RetrofitInstance.bookingService.submitRepairRequest(
                dtoJson = dtoJsonBody,
                images = imageParts
            )

            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                val errorMessage = parseApiError(response.errorBody())
                NetworkResult.Error(errorMessage, response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Something wrong happen.")
        }
    }

    private fun parseApiError(errorBody: ResponseBody?): String {
        return try {
            val json = errorBody?.string() ?: return "Unknown Error"
            val obj = JSONObject(json)
            obj.optString("message", "Unknown Error.")
        } catch (e: Exception) {
            "Unknown Error."
        }
    }

    private fun handleApiError(response: retrofit2.Response<*>) {
        when (response.code()) {
            401 -> {
                // Token expired - AuthInterceptor sẽ xử lý
                Log.w("BookingRepository", "Token expired - handled by interceptor")
            }
            500 -> {
                Log.e("BookingRepository", "Server error: ${response.message()}")
            }
            else -> {
                Log.w("BookingRepository", "API error: ${response.code()} - ${response.message()}")
            }
        }
    }
}
