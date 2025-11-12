package com.example.garapro.data.remote


import com.example.garapro.data.model.emergencies.AutoCompleteResponse
import com.example.garapro.data.model.emergencies.DirectionResponse
import com.example.garapro.data.model.emergencies.GeocodeResponse
import com.example.garapro.data.model.emergencies.PlaceDetailResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface GoongApiService {
    @GET("Place/AutoComplete")
    fun getAutoComplete(
        @Query("input") input: String?,
        @Query("api_key") apiKey: String?
    ): Call<AutoCompleteResponse?>?

    @GET("Place/Detail")
    fun getPlaceDetail(
        @Query("place_id") placeId: String?,
        @Query("api_key") apiKey: String?
    ): Call<PlaceDetailResponse?>?

    @GET("Direction")
    fun getDirection(
        @Query("origin") origin: String?,
        @Query("destination") destination: String?,
        @Query("vehicle") vehicle: String?,
        @Query("api_key") apiKey: String?
    ): Call<DirectionResponse?>?

    @GET("Geocode")
    fun geocode(
        @Query("latlng") latlng: String?,
        @Query("api_key") apiKey: String?
    ): Call<GeocodeResponse?>?
}