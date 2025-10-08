package com.example.garapro.data.model

import org.joda.time.DateTime

data class User(
    val gender: Boolean?=null,
    val firstName: String?=null,
    val lastName: String?=null,
    val avatar: String?=null,
    val dateOfBirth: DateTime?=null,
    val email: String?=null,
    val phoneNumber: String?=null
)