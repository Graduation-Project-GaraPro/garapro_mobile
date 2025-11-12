// PaymentViewModel.kt
package com.example.garapro.ui.payments


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.payments.CreatePaymentRequest
import com.example.garapro.data.model.payments.CreatePaymentResponse
import com.example.garapro.data.model.payments.PaymentStatusDto
import com.example.garapro.data.repository.PaymentRepository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PaymentViewModel(
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _paymentState = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val paymentState: StateFlow<PaymentState> = _paymentState.asStateFlow()

    private val _paymentLink = MutableStateFlow<CreatePaymentResponse?>(null)
    val paymentLink: StateFlow<CreatePaymentResponse?> = _paymentLink.asStateFlow()

    private val _pollingState = MutableStateFlow<Result<PaymentStatusDto>?>(null)
    val pollingState: StateFlow<Result<PaymentStatusDto>?> = _pollingState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun createPaymentLink(request: CreatePaymentRequest) {
        viewModelScope.launch {
            _paymentState.value = PaymentState.Loading
            try {
                val result = paymentRepository.createPaymentLink(request)
                if (result.isSuccess) {
                    _paymentLink.value = result.getOrNull()
                    _paymentState.value = PaymentState.Success
                } else {
                    _paymentState.value = PaymentState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                _paymentState.value = PaymentState.Error(e.message ?: "Payment failed")
            }
        }
    }

    fun startPollingStatus(orderCode: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _pollingState.value = null

            val result = paymentRepository.pollStatus(orderCode)
            Log.d("status",result.toString())
            _pollingState.value = result
            _isLoading.value = false
        }
    }

    fun clearPaymentState() {
        _paymentState.value = PaymentState.Idle
        _paymentLink.value = null
        _pollingState.value = null
    }
}

sealed class PaymentState {
    object Idle : PaymentState()
    object Loading : PaymentState()
    object Success : PaymentState()
    data class Error(val message: String) : PaymentState()
}