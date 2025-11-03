package com.example.garapro.ui.RepairProgress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.RepairProgresses.FilterChipData
import com.example.garapro.data.model.RepairProgresses.FilterType
import com.example.garapro.data.model.RepairProgresses.OrderStatus
import com.example.garapro.data.model.RepairProgresses.PagedResult
import com.example.garapro.data.model.RepairProgresses.RepairOrderFilter
import com.example.garapro.data.model.RepairProgresses.RepairOrderListItem
import com.example.garapro.data.model.RepairProgresses.RepairProgressDetail
import com.example.garapro.data.model.RepairProgresses.RoType
import com.example.garapro.data.remote.RetrofitInstance
import com.example.garapro.data.repository.RepairProgress.RepairProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RepairProgressViewModel : ViewModel() {

    private val repository = RepairProgressRepository(RetrofitInstance.RepairProgressService)

    private val _repairOrders = MutableStateFlow<RepairProgressRepository.ApiResponse<PagedResult<RepairOrderListItem>>>(
        RepairProgressRepository.ApiResponse.Loading())
    val repairOrders: StateFlow<RepairProgressRepository.ApiResponse<PagedResult<RepairOrderListItem>>> = _repairOrders

    private val _repairOrderDetail = MutableStateFlow<RepairProgressRepository.ApiResponse< RepairProgressDetail>?>(null)
    val repairOrderDetail: StateFlow<RepairProgressRepository.ApiResponse<RepairProgressDetail>?> = _repairOrderDetail

    private val _orderStatuses = MutableStateFlow<List<OrderStatus>>(emptyList())
    val orderStatuses: StateFlow<List<OrderStatus>> = _orderStatuses

    private val _filterState = MutableStateFlow(RepairOrderFilter())
    val filterState: StateFlow<RepairOrderFilter> = _filterState

    private val _showFilter = MutableStateFlow(false)
    val showFilter: StateFlow<Boolean> = _showFilter

    private val _filterChips = MutableStateFlow<List<FilterChipData>>(emptyList())
    val filterChips: StateFlow<List<FilterChipData>> = _filterChips

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadOrderStatuses()
        loadRepairOrders()
    }

    fun loadRepairOrders() {
        viewModelScope.launch {
            _isLoading.value = true
            _repairOrders.value = RepairProgressRepository.ApiResponse.Loading()
            val result = repository.getMyRepairOrders(_filterState.value)
            _repairOrders.value = result
            updateFilterChips()
            _isLoading.value = false
        }
    }

    fun loadRepairOrderDetail(repairOrderId: String) {
        viewModelScope.launch {
            _repairOrderDetail.value = RepairProgressRepository.ApiResponse.Loading()
            val result = repository.getRepairOrderDetail(repairOrderId)
            _repairOrderDetail.value = result
        }
    }

    private fun loadOrderStatuses() {
        viewModelScope.launch {
            val result = repository.getOrderStatuses()
            if (result is RepairProgressRepository.ApiResponse.Success) {
                _orderStatuses.value = result.data
            }
        }
    }

    fun updateFilter(newFilter: RepairOrderFilter) {
        _filterState.value = newFilter
        loadRepairOrders()
    }

    fun toggleFilterVisibility() {
        _showFilter.value = !_showFilter.value
    }

    fun clearFilter() {
        _filterState.value = RepairOrderFilter()
        loadRepairOrders()
    }

    fun updateStatusFilter(statusId: Int?) {
        _filterState.value = _filterState.value.copy(statusId = statusId)
        loadRepairOrders()
    }

    fun updateRoTypeFilter(roType: RoType?) {
        _filterState.value = _filterState.value.copy(roType = roType)
        loadRepairOrders()
    }

    fun updatePaidStatusFilter(paidStatus: String?) {
        _filterState.value = _filterState.value.copy(paidStatus = paidStatus)
        loadRepairOrders()
    }

    fun updateDateFilter(fromDate: String?, toDate: String?) {
        _filterState.value = _filterState.value.copy(
            fromDate = fromDate,
            toDate = toDate
        )
        loadRepairOrders()
    }

    fun removeFilterChip(chipId: String) {
        val chip = _filterChips.value.find { it.id == chipId }
        chip?.let {
            when (it.type) {
                FilterType.STATUS -> updateStatusFilter(null)
                FilterType.RO_TYPE -> updateRoTypeFilter(null)
                FilterType.PAID_STATUS -> updatePaidStatusFilter(null)
                FilterType.DATE -> updateDateFilter(null, null)
            }
        }
    }

    private fun updateFilterChips() {
        val chips = mutableListOf<FilterChipData>()

        _filterState.value.statusId?.let { statusId ->
            val statusName = _orderStatuses.value.find { it.orderStatusId == statusId }?.statusName ?: "Status"
            chips.add(FilterChipData("status_$statusId", statusName, FilterType.STATUS, true))
        }

        _filterState.value.roType?.let { roType ->
            chips.add(FilterChipData("roType_${roType.name}", roType.name, FilterType.RO_TYPE, true))
        }

        _filterState.value.paidStatus?.let { paidStatus ->
            chips.add(FilterChipData("paid_$paidStatus", paidStatus, FilterType.PAID_STATUS, true))
        }

        if (_filterState.value.fromDate != null || _filterState.value.toDate != null) {
            val dateText = "${_filterState.value.fromDate ?: ""} - ${_filterState.value.toDate ?: ""}"
            chips.add(FilterChipData("date_range", dateText, FilterType.DATE, true))
        }

        _filterChips.value = chips
    }
}