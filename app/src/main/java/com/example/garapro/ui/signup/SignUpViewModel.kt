package com.example.garapro.ui.signup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.SignupRequest
import com.example.garapro.data.model.SignupResponse
import com.example.garapro.data.repository.AuthRepository
import com.example.garapro.utils.Resource
import kotlinx.coroutines.launch

class SignupViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _signupState = MutableLiveData<Resource<SignupResponse>>()
    val signupState: LiveData<Resource<SignupResponse>> = _signupState

    fun signup(signupRequest: SignupRequest) {
        viewModelScope.launch {
            repository.signup(signupRequest).collect { result ->
                _signupState.value = result
            }
        }
    }
}