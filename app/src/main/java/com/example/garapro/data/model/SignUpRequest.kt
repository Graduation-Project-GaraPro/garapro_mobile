package com.example.garapro.data.model

data class SignupRequest(
    val email: String,
    val password: String,
    val confirmPassword: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String
)

data class otpRequest(
    val phoneNumber: String
)

data class otpVerifyRequest(
    val phoneNumber: String,
    val token: String,
)