package com.example.garapro.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.ImageResponse
import com.example.garapro.data.model.User
import com.example.garapro.data.repository.UserRepository
import com.example.garapro.utils.Resource
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class ProfileViewModel(private val repository: UserRepository) : ViewModel() {

    // Trạng thái lấy thông tin user
    private val _userState = MutableLiveData<Resource<User>>()
    val userState: LiveData<Resource<User>> get() = _userState

    // Trạng thái cập nhật user
    private val _updateState = MutableLiveData<Resource<User>>()
    val updateState: LiveData<Resource<User>> get() = _updateState

    // Trạng thái upload ảnh
    private val _uploadState = MutableLiveData<Resource<ImageResponse>>()
    val uploadState: LiveData<Resource<ImageResponse>> get() = _uploadState

    /** 🔹 Lấy thông tin người dùng */
    fun loadUserInfo() {
        viewModelScope.launch {
            repository.getMe().collect { result ->
                _userState.value = result
            }
        }
    }

    /** 🔹 Cập nhật thông tin người dùng */
    fun updateUser(user: User) {
        viewModelScope.launch {
            _updateState.value = Resource.Loading()
            val result = repository.updateUser(user)
            _updateState.value = result
            if (result is Resource.Success) {
                // cập nhật luôn LiveData userState để đồng bộ với ProfileFragment
                _userState.value = result
            }
        }
    }

    /** 🔹 Upload ảnh */
    fun uploadImage(filePart: MultipartBody.Part) {
        viewModelScope.launch {
            _uploadState.value = Resource.Loading()
            val result = repository.uploadImage(filePart)
            _uploadState.value = result
        }
    }
}
