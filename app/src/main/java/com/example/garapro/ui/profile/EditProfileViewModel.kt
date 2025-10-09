package com.example.garapro.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.User
import com.example.garapro.data.model.ImageResponse
import com.example.garapro.data.repository.UserRepository
import com.example.garapro.utils.Resource
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class EditProfileViewModel(private val repository: UserRepository) : ViewModel() {


}
