package com.example.garapro.data.model


data class otpResponse(
    val message: String
)

data class SignupResponse(
    val userId: String,
    val message: String?,
)