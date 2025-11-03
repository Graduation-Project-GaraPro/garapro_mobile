package com.example.garapro.ui.RepairProgress

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garapro.R
import com.example.garapro.data.model.RepairProgresses.Label
import com.example.garapro.data.model.RepairProgresses.RepairProgressDetail
import com.example.garapro.data.repository.RepairProgress.RepairProgressRepository
import com.example.garapro.databinding.FragmentRepairProgressDetailBinding
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class RepairProgressDetailFragment : Fragment() {

    private var _binding: FragmentRepairProgressDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RepairProgressDetailViewModel by viewModels()
    private lateinit var jobAdapter: JobAdapter

    private var repairOrderId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepairProgressDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        getRepairOrderIdFromArguments()
        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        loadRepairOrderDetail()
    }

    private fun getRepairOrderIdFromArguments() {
        repairOrderId = arguments?.getString(ARG_REPAIR_ORDER_ID)
        if (repairOrderId.isNullOrEmpty()) {
            showError("Repair order ID not found")
            findNavController().navigateUp()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        jobAdapter = JobAdapter()
        binding.jobsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = jobAdapter
            addItemDecoration(DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL))
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.repairOrderDetail.collect { response ->
                when (response) {
                    is RepairProgressRepository.ApiResponse.Loading -> {
                        showLoading(true)
                    }
                    is RepairProgressRepository.ApiResponse.Success -> {
                        showLoading(false)
                        bindRepairOrderDetail(response.data)
                    }
                    is RepairProgressRepository.ApiResponse.Error -> {
                        showLoading(false)
                        showError(response.message)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun loadRepairOrderDetail() {
        repairOrderId?.let { id ->
            viewModel.loadRepairOrderDetail(id)
        } ?: showError("Repair order ID is null")
    }

    private fun bindRepairOrderDetail(detail: RepairProgressDetail) {
        binding.apply {
            // Vehicle Info
            vehicleInfo.text = "${detail.vehicle.licensePlate} • ${detail.vehicle.model} • ${detail.vehicle.year}"
            vehicleBrand.text = detail.vehicle.brand

            // Order Details
            receiveDate.text = formatDate(detail.receiveDate)
            estimatedCompletionDate.text = formatDate(detail.estimatedCompletionDate)
            roType.text = getROTypeNameVietnamese(detail.roType)
            paidStatus.text = getPaidStatusVietnamese(detail.paidStatus)
            note.text = detail.note ?: "No note"

            // Financial Info
            estimatedAmount.text = formatCurrency(detail.estimatedAmount)
            paidAmount.text = formatCurrency(detail.paidAmount)
            cost.text = formatCurrency(detail.cost)

            // Progress Section
            progressStatus.text = detail.progressStatus
            progressPercentage.text = "${detail.progressPercentage}%"
            progressJobBar.progress = detail.progressPercentage
            statusName.text = getStatusNameVietnamese(detail.orderStatus.statusName)

            // Set status color based on status
            setStatusColor(detail.orderStatus.statusName)

            // Setup labels
//            setupLabels(detail.orderStatus.labels)

            // Jobs
            jobAdapter.submitList(detail.jobs)

            // Show/hide completion date if available
            detail.completionDate?.let { completionDate ->
                // You can add completion date to the layout if needed
            }
        }
    }

    private fun setupLabels(labels: List<Label>) {
//        binding.labelsContainer.removeAllViews()
//        labels.forEach { label ->
//            val chip = Chip(requireContext()).apply {
//                text = label.labelName
//                setTextColor(Color.parseColor(label.hexCode))
//                chipStrokeColor = ColorStateList.valueOf(Color.parseColor(label.hexCode))
//                chipStrokeWidth = 1f
//                setChipBackgroundColorResource(android.R.color.transparent)
//            }
//            binding.labelsContainer.addView(chip)
//        }
    }

    private fun getJobStatusNameVietnamese(statusName: String): String {
        return when (statusName) {
            "Pending" -> "Đang chờ"
            "New" -> "Mới"
            "InProgress" -> "Đang sửa"
            "Completed" -> "Hoàn tất"
            "OnHold" -> "Tạm dừng"
            else -> "Không xác định"
        }
    }
    private fun setStatusColor(statusName: String) {
        val color = when (statusName) {
            "Completed" -> ContextCompat.getColor(requireContext(), R.color.green)
            "In Progress" -> ContextCompat.getColor(requireContext(), R.color.blue)
            "Pending" -> ContextCompat.getColor(requireContext(), R.color.orange)
            "Cancelled" -> ContextCompat.getColor(requireContext(), R.color.red)
            else -> ContextCompat.getColor(requireContext(), R.color.gray)
        }
        binding.statusName.setBackgroundColor(color)
    }

    private fun getStatusNameVietnamese(statusName: String): String{
        return when (statusName) {
            "Completed" -> "Hoàn tất"
            "In Progress" -> "Đang sửa"
            "Pending" -> "Đang xử lý"
            else -> "Không xác định"
        }
    }
    private fun getPaidStatusVietnamese(statusName: String): String{
        return when (statusName) {
            "Paid" -> "Đã thanh toán"
            "Partial" -> "Trả 1 phần"
            "Pending" -> "chưa thanh toán"
            else -> "Không xác định"
        }
    }

    private fun getROTypeNameVietnamese(statusName: String): String{
        return when (statusName) {
            "WalkIn" -> "vãng lai"
            "Scheduled" -> "Đặt lịch"
            "Breakdown" -> "Sự cố"
            else -> "Không xác định"
        }
    }


    private fun formatDate(dateString: String?): String {
        if (dateString == null) return "N/A"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            dateString
        }
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(amount)
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Retry") {
                loadRepairOrderDetail()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_REPAIR_ORDER_ID = "repair_order_id"

        fun newInstance(repairOrderId: String): RepairProgressDetailFragment {
            val args = Bundle().apply {
                putString(ARG_REPAIR_ORDER_ID, repairOrderId)
            }
            val fragment = RepairProgressDetailFragment()
            fragment.arguments = args
            return fragment
        }
    }
}