package com.example.garapro.ui.signup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.otpResponse
import com.example.garapro.data.repository.AuthRepository
import com.example.garapro.utils.Resource
import kotlinx.coroutines.launch

class OtpViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _otpSendState = MutableLiveData<Resource<otpResponse>>()
    val otpSendState: LiveData<Resource<otpResponse>> = _otpSendState

    private val _otpVerifyState = MutableLiveData<Resource<otpResponse>>()
    val otpVerifyState: LiveData<Resource<otpResponse>> = _otpVerifyState

    fun sendOtp(phone: String) {
        viewModelScope.launch {
            repository.sendOtp(phone).collect { _otpSendState.value = it }
        }
    }

    fun verifyOtp(phone: String, otp: String) {
        viewModelScope.launch {
            repository.verifyOtp(phone, otp).collect { _otpVerifyState.value = it }
        }
    }
}
