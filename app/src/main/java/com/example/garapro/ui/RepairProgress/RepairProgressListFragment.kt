package com.example.garapro.ui.RepairProgress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garapro.R
import com.example.garapro.data.model.RepairProgresses.FilterChipData
import com.example.garapro.data.model.RepairProgresses.RepairOrderFilter
import com.example.garapro.data.model.RepairProgresses.RoType
import com.example.garapro.data.repository.RepairProgress.RepairProgressRepository
import com.example.garapro.databinding.FragmentRepairProgressListBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import kotlinx.coroutines.launch

class RepairProgressListFragment : Fragment() {

    private lateinit var binding: FragmentRepairProgressListBinding
    private val viewModel: RepairProgressViewModel by viewModels()
    private lateinit var adapter: RepairOrderAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentRepairProgressListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilter()
        observeViewModel()
        setupSwipeRefresh()
    }

    private fun setupRecyclerView() {
        adapter = RepairOrderAdapter { repairOrder ->
            navigateToDetail(repairOrder.repairOrderId)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RepairProgressListFragment.adapter
            addItemDecoration(DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL))
        }
    }

    private fun setupFilter() {
        // Filter button
        binding.filterButton.setOnClickListener {
            viewModel.toggleFilterVisibility()
        }

        // Clear filter button
        binding.clearFilterButton.setOnClickListener {
            viewModel.clearFilter()
        }

        // Setup filter options
        setupFilterOptions()
    }

    private fun setupFilterOptions() {
        // Status filter

        binding.statusFilterLayout.setEndIconOnClickListener {
            showStatusFilterDialog()
        }
        binding.statusFilter.setOnClickListener {
            showStatusFilterDialog()
        }

        // RO Type filter
        binding.roTypeFilterLayout.setEndIconOnClickListener {
            showRoTypeFilterDialog()
        }
        binding.roTypeFilter.setOnClickListener {
            showRoTypeFilterDialog()
        }

        // Paid status filter
        binding.paidStatusFilterLayout.setEndIconOnClickListener {
            showPaidStatusFilterDialog()
        }
        binding.paidStatusFilter.setOnClickListener {
            showPaidStatusFilterDialog()
        }

        // Date filter
        binding.dateFilter.setOnClickListener {
            showDateRangePicker()
        }

        // Apply filter button
//        binding.applyFilterButton.setOnClickListener {
//            viewModel.toggleFilterVisibility()
//            // Không cần gọi loadRepairOrders() vì đã được gọi khi update filter
//        }
    }

    private fun showStatusFilterDialog() {
        val statuses = viewModel.orderStatuses.value
        val items = statuses.map { it.statusName }.toTypedArray()
        val checkedItem = viewModel.filterState.value.statusId?.let { statusId ->
            statuses.indexOfFirst { it.orderStatusId == statusId }
        } ?: -1

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Lọc theo trạng thái")
            .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                val selectedStatus = statuses.getOrNull(which)
                viewModel.updateStatusFilter(selectedStatus?.orderStatusId)
                dialog.dismiss()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }


    private fun showRoTypeFilterDialog() {
        val roTypes = arrayOf("Khách vãng lai", "Đã lên lịch", "Sự cố")
        val currentFilter = viewModel.filterState.value.roType
        val checkedItem = currentFilter?.ordinal ?: -1

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Lọc theo loại RO")
            .setSingleChoiceItems(roTypes, checkedItem) { dialog, which ->
                val selectedType = when (which) {
                    0 -> RoType.WalkIn
                    1 -> RoType.Scheduled
                    2 -> RoType.Breakdown
                    else -> null
                }
                viewModel.updateRoTypeFilter(selectedType)
                dialog.dismiss()
            }
            .setNegativeButton("Xóa") { dialog, _ ->
                viewModel.updateRoTypeFilter(null)
                dialog.dismiss()
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPaidStatusFilterDialog() {
        val paidStatuses = arrayOf("Chờ thanh toán", "Thanh toán một phần", "Đã thanh toán")
        val currentFilter = viewModel.filterState.value.paidStatus
        val checkedItem = when (currentFilter) {
            "Pending" -> 0
            "Partial" -> 1
            "Paid" -> 2
            else -> -1
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Lọc theo trạng thái thanh toán")
            .setSingleChoiceItems(paidStatuses, checkedItem) { dialog, which ->
                val selectedStatus = when (which) {
                    0 -> "Pending"
                    1 -> "Partial"
                    2 -> "Paid"
                    else -> null
                }
                viewModel.updatePaidStatusFilter(selectedStatus)
                dialog.dismiss()
            }
            .setNegativeButton("Xóa") { dialog, _ ->
                viewModel.updatePaidStatusFilter(null)
                dialog.dismiss()
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDateRangePicker() {
        // Implement date range picker dialog
        // You can use MaterialDatePicker for this
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.repairOrders.collect { response ->
                when (response) {
                    is RepairProgressRepository.ApiResponse.Loading -> {
                        if (!binding.swipeRefresh.isRefreshing) {
                            binding.progressBar.visibility = View.VISIBLE
                        }
                        binding.emptyState.visibility = View.GONE
                    }
                    is RepairProgressRepository.ApiResponse.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false

                        val orders = response.data.items
                        adapter.submitList(orders)

                        binding.emptyState.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
                        binding.emptyText.text = if (viewModel.filterChips.value.isNotEmpty()) {
                            "Không có đơn hàng nào phù hợp với bộ lọc"
                        } else {
                            "Không tìm thấy đơn sửa chữa"
                        }
                    }
                    is RepairProgressRepository.ApiResponse.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        showError(response.message)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.showFilter.collect { show ->
                binding.filterContainer.visibility = if (show) View.VISIBLE else View.GONE
                binding.filterButton.setImageResource(
                    if (show) R.drawable.ic_ft_expand_less else R.drawable.ic_filter
                )

            }
        }

//        viewLifecycleOwner.lifecycleScope.launch {
//            viewModel.filterChips.collect { chips ->
//                updateFilterChips(chips)
//            }
//        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filterState.collect { filter ->
                updateFilterInputs(filter)
            }
        }
    }

    private fun updateFilterInputs(filter: RepairOrderFilter) {
        // Cập nhật trạng thái
        filter.statusId?.let { statusId ->
            val statusName = viewModel.orderStatuses.value.find { it.orderStatusId == statusId }?.statusName ?: "Trạng thái"
            var statusVietnamese= when (statusName) {
                "Completed" -> "Hoàn tất"
                "In Progress" -> "Đang sửa"
                "Pending" -> "Đang xử lý"
                else -> {"Không xác định"}
            }
            binding.statusFilter.setText(statusVietnamese)
        } ?: run {
            binding.statusFilter.setText("")
        }

        // Cập nhật loại RO
        filter.roType?.let { roType ->
            binding.roTypeFilter.setText(
                when (roType) {
                    RoType.WalkIn -> "Khách vãng lai"
                    RoType.Scheduled -> "Đã lên lịch"
                    RoType.Breakdown -> "Sự cố"
                }
            )
        } ?: run {
            binding.roTypeFilter.setText("")
        }

        // Cập nhật trạng thái thanh toán
        filter.paidStatus?.let { paidStatus ->
            binding.paidStatusFilter.setText(
                when (paidStatus) {
                    "Pending" -> "Chờ thanh toán"
                    "Partial" -> "Thanh toán một phần"
                    "Paid" -> "Đã thanh toán"
                    else -> paidStatus
                }
            )
        } ?: run {
            binding.paidStatusFilter.setText("")
        }

        // Cập nhật ngày
        if (filter.fromDate != null || filter.toDate != null) {
            val fromText = filter.fromDate ?: ""
            val toText = filter.toDate ?: ""
            binding.dateFilter.text = "$fromText - $toText"
        } else {
            binding.dateFilter.text = "Chọn khoảng ngày"
        }
    }

    private fun updateFilterChips(chips: List<FilterChipData>) {
        binding.filterChipGroup.removeAllViews()

        chips.forEach { chipData ->
            val chip = Chip(requireContext()).apply {
                text = chipData.text
                isCloseIconVisible = true
                tag = chipData.id
                setOnCloseIconClickListener {
                    viewModel.removeFilterChip(chipData.id)
                }
            }
            binding.filterChipGroup.addView(chip)
        }

        binding.filterChipGroup.visibility = if (chips.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadRepairOrders()
        }
    }

    private fun navigateToDetail(repairOrderId: String) {
        val bundle = Bundle().apply {
            putString("repair_order_id", repairOrderId)
        }
        findNavController().navigate(R.id.action_repairTrackingFragment_to_repairProgressDetailFragment, bundle)
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}