package com.example.garapro.ui.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.garapro.data.repository.repairRequest.BookingRepository

class BookingViewModelFactory(private val repository: BookingRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppointmentsViewModel::class.java)) {
            return AppointmentsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}