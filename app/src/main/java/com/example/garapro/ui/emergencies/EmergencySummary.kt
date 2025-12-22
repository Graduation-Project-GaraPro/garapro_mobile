package com.example.garapro.ui.emergencies

import android.os.Parcel
import android.os.Parcelable

data class EmergencySummary(
    val id: String,
    val vehicleTitle: String,
    val issue: String,
    val status: String,
    val time: String,
    val garageName: String,
    val technicianName: String? = null,
    val technicianPhone: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readString()
    )
    override fun describeContents(): Int = 0
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(vehicleTitle)
        parcel.writeString(issue)
        parcel.writeString(status)
        parcel.writeString(time)
        parcel.writeString(garageName)
        parcel.writeString(technicianName)
        parcel.writeString(technicianPhone)
    }
    companion object CREATOR : Parcelable.Creator<EmergencySummary> {
        override fun createFromParcel(parcel: Parcel): EmergencySummary = EmergencySummary(parcel)
        override fun newArray(size: Int): Array<EmergencySummary?> = arrayOfNulls(size)
    }
}