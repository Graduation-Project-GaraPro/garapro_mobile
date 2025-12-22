package com.example.garapro.data.remote

import com.example.garapro.data.model.emergencies.Emergency
import com.example.garapro.data.model.emergencies.CreateEmergencyRequest
import com.example.garapro.data.model.emergencies.Garage
import com.example.garapro.data.model.emergencies.NearbyBranchDto
import com.example.garapro.data.model.emergencies.PriceEmergencyResponse
import com.example.garapro.data.model.emergencies.RouteResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface EmergencyApiService {
    @POST("EmergencyRequest/create")
    suspend fun createEmergencyRequest(@Body request: CreateEmergencyRequest): Response<Emergency>

    @GET("EmergencyRequest/nearby-branches")
    suspend fun getNearbyBranches(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("count") count: Int = 5
    ): Response<List<NearbyBranchDto>>

    @GET("PriceEmergency/calculate-fee")
    suspend fun calculateEmergencyFee(
        @Query("distanceKm") distanceKm: Double
    ): Response<PriceEmergencyResponse>

    @GET("EmergencyRequest/route/{id}")
    suspend fun getRoute(
        @Path("id") emergencyId: String
    ): Response<RouteResponse>

    @GET("EmergencyRequest/route")
    suspend fun getRouteDirect(
        @Query("fromLat") fromLat: Double,
        @Query("fromLon") fromLon: Double,
        @Query("toLat") toLat: Double,
        @Query("toLon") toLon: Double
    ): Response<RouteResponse>

    @GET("Emergencies/pending")
    suspend fun getPendingEmergencies(): Response<List<Emergency>>

    @POST("Emergencies/{id}/accept")
    suspend fun acceptEmergency(
        @Path("id") emergencyId: String
    ): Response<Emergency>

    @POST("EmergencyRequest/cancel/{emergenciesId}")
    suspend fun cancelEmergency(
        @Path("emergenciesId") emergenciesId: String
    ): Response<Void>

    @GET("EmergencyRequest/{id}")
    suspend fun getEmergencyById(
        @Path("id") emergencyId: String
    ): Response<Emergency>

    @GET("EmergencyRequest/customer/{customerId}")
    suspend fun getEmergenciesByCustomer(
        @Path("customerId") customerId: String
    ): Response<List<com.example.garapro.data.model.emergencies.EmergencyRequestSummary>>

    @GET("EmergencyRequest/garage/{id}")
    suspend fun getBranchById(@Path("id") id: String): Response<com.example.garapro.data.model.repairRequest.Branch>
}
