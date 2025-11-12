package com.example.garapro.ui.paymentResults


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.garapro.data.remote.RetrofitInstance
import com.example.garapro.data.repository.PaymentRepository
import com.example.garapro.ui.payments.PaymentViewModel
import kotlinx.coroutines.Dispatchers

class PaymentViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PaymentViewModel::class.java)) {
            val api = RetrofitInstance.paymentService
            val repo = PaymentRepository(api, Dispatchers.IO)
            return PaymentViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}