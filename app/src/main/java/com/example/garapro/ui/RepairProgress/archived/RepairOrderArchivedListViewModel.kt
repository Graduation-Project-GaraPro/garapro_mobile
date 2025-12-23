package com.example.garapro.ui.RepairProgress.archived

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.RepairProgresses.PagedResult
import com.example.garapro.data.model.RepairProgresses.RepairOrderArchivedFilter
import com.example.garapro.data.model.RepairProgresses.RepairOrderArchivedListItem
import com.example.garapro.data.repository.RepairProgress.RepairProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RepairOrderArchivedListViewModel(
    private val repository: RepairProgressRepository
) : ViewModel() {

    private val _ordersState =
        MutableLiveData<RepairProgressRepository.ApiResponse<PagedResult<RepairOrderArchivedListItem>>>()
    val ordersState: LiveData<RepairProgressRepository.ApiResponse<PagedResult<RepairOrderArchivedListItem>>>
        get() = _ordersState

    private val _filterState = MutableLiveData(RepairOrderArchivedFilter())
    val filterState: LiveData<RepairOrderArchivedFilter> get() = _filterState

    private var currentFilter = RepairOrderArchivedFilter()

    private var currentPage = 1
    private var totalPages = 1

    private val _isLoadingPage = MutableStateFlow(false)
    val isLoadingPage: StateFlow<Boolean> = _isLoadingPage

    fun loadOrders(filter: RepairOrderArchivedFilter? = null) {
        loadPage(page = 1, isLoadMore = false, filterOverride = filter)
    }

    private fun loadPage(
        page: Int,
        isLoadMore: Boolean,
        filterOverride: RepairOrderArchivedFilter? = null
    ) {
        if (_isLoadingPage.value) return

        val newFilter = (filterOverride ?: currentFilter).copy(pageNumber = page)
        currentFilter = newFilter
        _filterState.value = newFilter

        viewModelScope.launch {
            _isLoadingPage.value = true

            if (!isLoadMore) {
                _ordersState.value = RepairProgressRepository.ApiResponse.Loading()
            }

            val result = repository.getArchivedRepairOrders(newFilter)

            when (result) {
                is RepairProgressRepository.ApiResponse.Success -> {
                    val paged = result.data
                    currentPage = paged.pageNumber
                    totalPages = paged.totalPages

                    val newItems = paged.items ?: emptyList()

                    val mergedItems = if (isLoadMore) {
                        val oldItems =
                            (ordersState.value as? RepairProgressRepository.ApiResponse.Success)
                                ?.data
                                ?.items
                                ?: emptyList()
                        oldItems + newItems
                    } else {
                        newItems
                    }

                    val mergedPaged = paged.copy(items = mergedItems)
                    _ordersState.value = RepairProgressRepository.ApiResponse.Success(mergedPaged)
                }

                is RepairProgressRepository.ApiResponse.Error -> {
                    _ordersState.value = result
                }

                is RepairProgressRepository.ApiResponse.Loading -> {
                    // not used
                }
            }

            _isLoadingPage.value = false
        }
    }

    fun loadNextPage() {
        if (_isLoadingPage.value) return
        if (currentPage >= totalPages) return
        loadPage(page = currentPage + 1, isLoadMore = true)
    }

    fun refresh() {
        loadOrders(currentFilter)
    }

    fun clearFilter() {
        val default = RepairOrderArchivedFilter(
            fromDate = null,
            toDate = null,
            pageNumber = 1
        )
        currentFilter = default
        _filterState.value = default
        loadOrders(default)
    }

    fun updateDateRangeFilter(fromDate: String?, toDate: String?) {
        val updated = currentFilter.copy(
            fromDate = fromDate,
            toDate = toDate,
            pageNumber = 1
        )
        loadOrders(updated)
    }
}
