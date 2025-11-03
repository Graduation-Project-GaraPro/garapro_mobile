package com.example.garapro.ui.quotations

import android.util.Log

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.quotations.Quotation
import com.example.garapro.data.model.quotations.QuotationStatus
import com.example.garapro.data.repository.QuotationRepository
import kotlinx.coroutines.launch


class QuotationListViewModel(
    private val repository: QuotationRepository
) : ViewModel() {

    private val _quotations = MutableLiveData<List<Quotation>>()
    val quotations: LiveData<List<Quotation>> = _quotations

    private val _selectedStatus = MutableLiveData<QuotationStatus?>()
    val selectedStatus: LiveData<QuotationStatus?> = _selectedStatus

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _paginationInfo = MutableLiveData<PaginationInfo?>()
    val paginationInfo: LiveData<PaginationInfo?> = _paginationInfo

    init {
        loadQuotations()
    }

    fun loadQuotations(pageNumber: Int = 1, pageSize: Int = 10) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = repository.getQuotations(pageNumber, pageSize, _selectedStatus.value)
            if (result.isSuccess) {
                val data = result.getOrNull()
                Log.d("Quotations",data?.data.toString());
                data?.let {
                    if (pageNumber == 1) {
                        _quotations.value = it.data
                    } else {
                        _quotations.value = _quotations.value.orEmpty() + it.data
                    }

                    _paginationInfo.value = PaginationInfo(
                        pageNumber = it.pageNumber,
                        pageSize = it.pageSize,
                        totalCount = it.totalCount,
                        totalPages = it.totalPages
                    )
                }
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
            }

            _isLoading.value = false
        }
    }


    fun filterByStatus(status: QuotationStatus?) {
        _selectedStatus.value = status
        loadQuotations(1)
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

data class PaginationInfo(
    val pageNumber: Int,
    val pageSize: Int,
    val totalCount: Int,
    val totalPages: Int
)