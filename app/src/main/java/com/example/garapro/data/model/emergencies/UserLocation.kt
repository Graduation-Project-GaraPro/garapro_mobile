package com.example.garapro.data.model.emergencies

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val address: String? = null
) : Parcelable