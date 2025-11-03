package com.example.garapro.data.repository.repairRequest

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.model.repairRequest.Branch
import com.example.garapro.data.model.repairRequest.ChildCategoriesResponse
import com.example.garapro.data.model.repairRequest.ChildServiceCategory
import com.example.garapro.data.model.repairRequest.CreateRepairRequest
import com.example.garapro.data.model.repairRequest.ParentServiceCategory
import com.example.garapro.data.model.repairRequest.Part
import com.example.garapro.data.model.repairRequest.PartCategory
import com.example.garapro.data.model.repairRequest.RepairRequest
import com.example.garapro.data.model.repairRequest.RepairRequestDetail

import com.example.garapro.data.model.repairRequest.Service
import com.example.garapro.data.model.repairRequest.ServiceCategory
import com.example.garapro.data.model.repairRequest.ServiceCategoryInfo
import com.example.garapro.data.model.repairRequest.Vehicle
import com.example.garapro.data.remote.RetrofitInstance
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class BookingRepository(
    private val context: Context,
    private val tokenManager: TokenManager
) {

    // Parent Service Categories
    suspend fun getParentServiceCategories(): List<ParentServiceCategory> {
        return try {
            val response = RetrofitInstance.bookingService.getParentServiceCategories()
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                getMockParentServiceCategories()
            }
        } catch (e: Exception) {
            getMockParentServiceCategories()
        }
    }

    // Thêm vào BookingRepository.kt
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
                Log.w("BookingRepository", "API getRepairRequestsPaged failed: ${response.code()}")
                getMockRepairRequests() // Fallback to mock data
            }
        } catch (e: Exception) {
            Log.e("BookingRepository", "Error getting repair requests: ${e.message}")
            getMockRepairRequests() // Fallback to mock data
        }
    }
    // Child Service Categories with filtering
    suspend fun getChildServiceCategories(
        parentId: String,
        pageNumber: Int = 1,
        pageSize: Int = 10,
        childServiceCategoryId: String? = null,
        searchTerm: String? = null
    ): ChildCategoriesResponse {
        return try {
            val response = RetrofitInstance.bookingService.getChildServiceCategories(
                parentId = parentId,
                pageNumber = pageNumber,
                pageSize = pageSize,
                childServiceCategoryId = childServiceCategoryId,
                searchTerm = searchTerm
            )
            if (response.isSuccessful) {
                response.body() ?: getMockChildCategoriesResponse()
            } else {
                getMockChildCategoriesResponse()
            }
        } catch (e: Exception) {
            getMockChildCategoriesResponse()
        }
    }

    suspend fun getRepairRequestDetail(id: String): RepairRequestDetail? {
        return try {
            val response = RetrofitInstance.bookingService.getRepairRequestDetail(id)
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getMockParentServiceCategories(): List<ParentServiceCategory> {
        return listOf(
            ParentServiceCategory(
                serviceCategoryId = "1",
                categoryName = "Bảo dưỡng",
                parentServiceCategoryId = null,
                description = "Dịch vụ bảo dưỡng",
                isActive = true,
                createdAt = "2024-01-01",
                updatedAt = null,
                services = emptyList(),
                childCategories = listOf(
                    ChildServiceCategory(
                        serviceCategoryId = "1-1",
                        categoryName = "Bảo dưỡng định kỳ",
                        parentServiceCategoryId = "1",
                        description = "Bảo dưỡng theo định kỳ",
                        isActive = true,
                        createdAt = "2024-01-01",
                        updatedAt = null,
                        services = null,
                        childCategories = null
                    )
                )
            )
        )
    }

    private fun getMockChildCategoriesResponse(): ChildCategoriesResponse {
        return ChildCategoriesResponse(
            totalCount = 1,
            pageNumber = 1,
            pageSize = 10,
            data = getMockServiceCategories() // Sử dụng mock data cũ
        )
    }

    suspend fun getVehicles(): List<Vehicle> {
        return try {
            // Kiểm tra xem Retrofit đã được khởi tạo chưa
            val response = RetrofitInstance.bookingService.getVehicles()
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                Log.w("BookingRepository", "API getVehicles failed: ${response.code()}")
                getMockVehicles() // Fallback to mock data
            }
        } catch (e: Exception) {
            Log.e("BookingRepository", "Error getting vehicles: ${e.message}")
            getMockVehicles() // Fallback to mock data
        }
    }

    suspend fun getBranches(): List<Branch> {
        return try {
            val response = RetrofitInstance.bookingService.getBranches()
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                handleApiError(response)
                getMockBranches()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            getMockBranches()
        }
    }

    suspend fun getServiceCategories(): List<ServiceCategory> {
        return try {
            val response = RetrofitInstance.bookingService.getServiceCategories()
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                handleApiError(response)
                getMockServiceCategories()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            getMockServiceCategories()
        }
    }

    suspend fun submitRepairRequest(request: CreateRepairRequest): Boolean {
        return try {
            // Chuyển tất cả thông tin (trừ ảnh) thành JSON
            val dtoJson = Gson().toJson(request.copy(images = emptyList()))
            val dtoJsonBody = dtoJson.toRequestBody("text/plain".toMediaTypeOrNull())

            // Ảnh đã được xử lý sẵn trong ViewModel (List<MultipartBody.Part>)
            val imageParts = request.images

            val response = RetrofitInstance.bookingService.submitRepairRequest(
                dtoJson = dtoJsonBody,
                images = imageParts
            )

            response.isSuccessful
        } catch (e: Exception) {
            Log.e("BookingRepository", "Error submitting request: ${e.message}", e)
            false
        }
    }



    private fun createPartFromString(value: String): RequestBody {
        return RequestBody.create("text/plain".toMediaTypeOrNull(), value)
    }
    private fun handleApiError(response: retrofit2.Response<*>) {
        when (response.code()) {
            401 -> {
                // Token expired - AuthInterceptor sẽ xử lý
                println("Token expired - handled by interceptor")
            }
            500 -> {
                println("Server error: ${response.message()}")
            }
            else -> {
                println("API error: ${response.code()} - ${response.message()}")
            }
        }
    }
    private fun getMockRepairRequests(): List<RepairRequest> {
        return listOf(
            RepairRequest(
                repairRequestID = "mock-1",
                vehicleID = "mock-vehicle-1",
                userID = "mock-user-1",
                description = "Mock repair request 1",
                branchId = "mock-branch-1",
                requestDate = "2024-01-01T10:00:00",
                completedDate = null,
                status = 0,
                createdAt = "2024-01-01T09:00:00",
                updatedAt = null,
                estimatedCost = 100.0
            )
        )
    }
    // Mock data fallback
    private fun getMockVehicles(): List<Vehicle> {
        return listOf(
            Vehicle(
                vehicleID = "1",
                brandID = "1",
                userID = "user1",
                modelID = "1",
                colorID = "1",
                licensePlate = "51A-12345",
                vin = "VIN123456789",
                year = 2020,
                odometer = 15000,
                lastServiceDate = "2024-01-01",
                nextServiceDate = "2024-07-01",
                warrantyStatus = "Active",
                brandName = "Toyota",
                modelName = "Camry",
                colorName = "Đen"
            )
            // ... other mock vehicles
        )
    }

    private fun getMockBranches(): List<Branch> {
        return listOf(
            Branch(
                branchId = "6e194346-9c53-45f6-8300-3fe2f7cee235",
                branchName = "Central Branch",
                city = "HCMC",
                district = "District 1",
                ward = "Ward 1",
                phoneNumber = "0123456789",
                email = "central@garage.com",
                description = "Central garage branch",
                isActive = true
            )
            // ... other mock branches
        )
    }

    private fun getMockServiceCategories(): List<ServiceCategory> {
        return listOf(
            ServiceCategory(
                serviceCategoryId = "cc4013ec-5999-4b8f-a419-3be0917e2673",
                categoryName = "Maintenance",
                serviceTypeId = "00000000-0000-0000-0000-000000000000",
                parentServiceCategoryId = null,
                description = null,
                isActive = true,
                createdAt = "2024-01-01T00:00:00",
                updatedAt = null,
                services = listOf(
                    Service(
                        serviceId = "eee65d35-8011-4507-a334-8e213e7bed41",
                        serviceCategoryId = "cc4013ec-5999-4b8f-a419-3be0917e2673",
                        serviceName = "Oil Change",
                        description = "Standard Oil Change",
                        price = 300000.0,
                        discountedPrice = 280000.0,
                        estimatedDuration = 1,
                        isActive = true,
                        isAdvanced = false,
                        createdAt = "2025-10-15T02:12:25.6589254",
                        updatedAt = null,
                        serviceCategory = ServiceCategoryInfo(
                            serviceCategoryId = "cc4013ec-5999-4b8f-a419-3be0917e2673",
                            categoryName = "Maintenance",
                            serviceTypeId = "00000000-0000-0000-0000-000000000000",
                            parentServiceCategoryId = null,
                            description = null,
                            isActive = true,
                            createdAt = "2024-01-01T00:00:00",
                            updatedAt = null
                        ),
                        partCategories = listOf(
                            PartCategory(
                                partCategoryId = "b20c60a1-3394-48c5-8dc0-b6ed1bd0f7a4",
                                categoryName = "Engine",
                                parts = listOf(
                                    Part(
                                        partId = "3896c125-752c-479c-b587-ea6b83a908e5",
                                        name = "Air Filter",
                                        price = 150000.0,
                                        stock = 10
                                    )
                                )
                            )
                        )
                    )
                ),
                childCategories = null
            )
        )
    }
}
