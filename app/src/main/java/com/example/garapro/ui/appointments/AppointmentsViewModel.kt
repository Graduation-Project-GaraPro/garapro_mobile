package com.example.garapro.ui.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.model.repairRequest.Branch
import com.example.garapro.data.model.repairRequest.RepairRequest
import com.example.garapro.data.model.repairRequest.Vehicle
import com.example.garapro.data.remote.BookingService
import com.example.garapro.data.repository.repairRequest.BookingRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// AppointmentsViewModel.kt
// AppointmentsViewModel.kt
class AppointmentsViewModel(private val repository: BookingRepository) : ViewModel() {

    private val _repairRequests = MutableStateFlow<List<RepairRequest>>(emptyList())
    val repairRequests: StateFlow<List<RepairRequest>> = _repairRequests.asStateFlow()

    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _branches = MutableStateFlow<List<Branch>>(emptyList())
    val branches: StateFlow<List<Branch>> = _branches.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentPage = 1
    private var hasMore = true
    private var currentStatus: Int? = null
    private var currentVehicleId: String? = null
    private var currentBranchId: String? = null

    // 🔹 Load dữ liệu ban đầu (vehicles, branches, và repair requests)
    fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val vehiclesDeferred = async { repository.getVehicles() }
                val branchesDeferred = async { repository.getBranches() }

                _vehicles.value = vehiclesDeferred.await()
                _branches.value = branchesDeferred.await()

                loadRepairRequests(1, true)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 🔹 Phân trang (load thêm)
    fun loadMoreData() {
        if (_isLoading.value || !hasMore) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                loadRepairRequests(currentPage + 1, false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 🔹 Làm mới danh sách (refresh)
    fun refreshData() {
        currentPage = 1
        hasMore = true

        viewModelScope.launch {
            _isLoading.value = true
            try {
                loadRepairRequests(currentPage, true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 🔹 Filter theo trạng thái
    fun filterByStatus(status: Int?) {
        currentStatus = status
        refreshData()
    }

    // 🔹 Filter theo xe
    fun filterByVehicle(vehicleId: String?) {
        currentVehicleId = vehicleId
        refreshData()
    }

    // 🔹 Filter theo chi nhánh
    fun filterByBranch(branchId: String?) {
        currentBranchId = branchId
        refreshData()
    }



    // 🔹 Gọi API lấy RepairRequests (đã hỗ trợ phân trang, filter)
    private suspend fun loadRepairRequests(page: Int, isRefresh: Boolean) {
        val result = repository.getRepairRequestsPaged(
            pageNumber = page,
            pageSize = 10,
            vehicleId = currentVehicleId,
            status = currentStatus,
            branchId = currentBranchId
        )

        _repairRequests.value = if (isRefresh) {
            result
        } else {
            _repairRequests.value + result
        }

        hasMore = result.size >= 10
        currentPage = page
    }


}
