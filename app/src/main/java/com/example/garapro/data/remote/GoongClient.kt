package com.example.garapro.data.remote

import com.example.garapro.data.remote.GoongClient.retrofit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object GoongClient {
     const val BASE_URL: String = "https://rsapi.goong.io/"
    var retrofit: Retrofit? = null

    fun getApiService(): GoongApiService {
        if (retrofit == null) {
            val logging = HttpLoggingInterceptor()
            logging.setLevel(HttpLoggingInterceptor.Level.BODY)

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create<GoongApiService?>(GoongApiService::class.java)
    }
}