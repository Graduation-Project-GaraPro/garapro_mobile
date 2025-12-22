package com.example.garapro.data.model

data class SecurityPolicy(
    val id: String,
    val minPasswordLength: Int,
    val requireSpecialChar: Boolean,
    val requireNumber: Boolean,
    val requireUppercase: Boolean
)