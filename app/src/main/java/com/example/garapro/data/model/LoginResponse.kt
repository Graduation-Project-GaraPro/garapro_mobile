package com.example.garapro.data.model

data class LoginResponse(
    val userId: String,
    val email: String,
    val token: String?,
    val roles: List<String>,
    val expiresIn: Int,
)