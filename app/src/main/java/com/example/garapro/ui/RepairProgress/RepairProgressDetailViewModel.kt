package com.example.garapro.ui.RepairProgress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.RepairProgresses.RepairOrderFilter
import com.example.garapro.data.model.RepairProgresses.RepairProgressDetail
import com.example.garapro.data.remote.RetrofitInstance
import com.example.garapro.data.repository.RepairProgress.RepairProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RepairProgressDetailViewModel : ViewModel() {

    private val repository = RepairProgressRepository(RetrofitInstance.RepairProgressService)

    private val _repairOrderDetail = MutableStateFlow<RepairProgressRepository.ApiResponse<RepairProgressDetail>?>(null)
    val repairOrderDetail: StateFlow<RepairProgressRepository.ApiResponse<RepairProgressDetail>?> = _repairOrderDetail

    fun loadRepairOrderDetail(repairOrderId: String) {
        viewModelScope.launch {
            _repairOrderDetail.value = RepairProgressRepository.ApiResponse.Loading()
            val result = repository.getRepairOrderDetail(repairOrderId)
            _repairOrderDetail.value = result
        }
    }
    fun RepairOrderFilter.hasActiveFilters(): Boolean {
        return statusId != null || roType != null || paidStatus != null || fromDate != null || toDate != null
    }
}