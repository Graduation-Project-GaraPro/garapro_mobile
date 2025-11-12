package com.example.garapro.data.local

import android.app.Application
import com.example.garapro.data.remote.RetrofitInstance
import com.example.garapro.data.remote.TokenExpiredListener

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val tokenManager = TokenManager(this)
        val tokenExpiredListener = object : TokenExpiredListener {
            override fun onTokenExpired() {
//                tokenManager.r
            }
        }



        RetrofitInstance.initialize(tokenManager, tokenExpiredListener)
    }
}