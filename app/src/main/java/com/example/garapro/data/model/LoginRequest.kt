package com.example.garapro.data.model

data class LoginRequest(
    val phoneNumber: String,
    val password: String,
    val rememberMe: Boolean
)