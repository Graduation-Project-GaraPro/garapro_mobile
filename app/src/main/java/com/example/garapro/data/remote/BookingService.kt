package com.example.garapro.data.remote


//import com.example.garapro.data.model.ApiResponse
import com.example.garapro.data.model.repairRequest.Branch
import com.example.garapro.data.model.repairRequest.ChildCategoriesResponse
import com.example.garapro.data.model.repairRequest.PagedRepairRequestResponse
import com.example.garapro.data.model.repairRequest.ParentServiceCategory
import com.example.garapro.data.model.repairRequest.RepairRequestDetail
import com.example.garapro.data.model.repairRequest.ServiceCategory
import com.example.garapro.data.model.repairRequest.Vehicle
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface BookingService {


    @GET("RepairRequest/{id}")
    suspend fun getRepairRequestDetail(@Path("id") id: String): Response<RepairRequestDetail>
    // Vehicles
    @GET("RepairRequest/paged")
    suspend fun getRepairRequestsPaged(
        @Query("pageNumber") pageNumber: Int,
        @Query("pageSize") pageSize: Int,
        @Query("vehicleId") vehicleId: String? = null,
        @Query("status") status: Int? = null,
        @Query("branchId") branchId: String? = null
    ): Response<PagedRepairRequestResponse>
    @GET("Vehicles/user")
    suspend fun getVehicles(): Response<List<Vehicle>>

    // Branches
    @GET("Branch/GetAllBranchesBasis")
    suspend fun getBranches(): Response<List<Branch>>

    // Service Categories
    @GET("ServiceCategories/forBooking")
    suspend fun getServiceCategories(): Response<List<ServiceCategory>>


    @GET("ServiceCategories/parents")
    suspend fun getParentServiceCategories(): Response<List<ParentServiceCategory>>


    @GET("ServiceCategories/fromParent/{parentId}")
    suspend fun getChildServiceCategories(
        @Path("parentId") parentId: String,
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 10,
        @Query("childServiceCategoryId") childServiceCategoryId: String? = null,
        @Query("searchTerm") searchTerm: String? = null
    ): Response<ChildCategoriesResponse>
    // Submit Repair Request
    @Multipart
    @POST("RepairRequest/withImage")
    suspend fun submitRepairRequest(
        @Part("dtoJson") dtoJson: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Response<ResponseBody>
}