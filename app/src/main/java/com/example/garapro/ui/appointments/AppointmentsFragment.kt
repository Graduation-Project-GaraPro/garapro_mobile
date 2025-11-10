package com.example.garapro.ui.appointments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.model.repairRequest.Branch
import com.example.garapro.data.model.repairRequest.RepairRequest
import com.example.garapro.data.model.repairRequest.Vehicle
import com.example.garapro.data.repository.repairRequest.BookingRepository
import com.example.garapro.databinding.FragmentAppointmentsBinding
import com.example.garapro.ui.repairRequest.BookingActivity
import kotlinx.coroutines.launch

class AppointmentsFragment : Fragment() {

    private lateinit var binding: FragmentAppointmentsBinding
    private lateinit var tokenManager: TokenManager
    private val repository by lazy { BookingRepository(requireContext(), tokenManager) }
    private val viewModelFactory by lazy { BookingViewModelFactory(repository) }
    private val viewModel: AppointmentsViewModel by viewModels { viewModelFactory }
    private lateinit var adapter: RepairRequestAdapter

    private var isFilterVisible = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAppointmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tokenManager = TokenManager(requireContext())

        setupRecyclerView()
        setupFilterSection()
        setupSwipeRefresh()
        setupCreateBookingButton()
        observeViewModel()

        viewModel.loadInitialData()
    }

    private fun setupRecyclerView() {
        adapter = RepairRequestAdapter(
            onItemClick = { showRepairRequestDetail(it) },
            onUpdateClick = { updateRepairRequest(it) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AppointmentsFragment.adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (!canScrollVertically(1)) {
                        viewModel.loadMoreData()
                    }
                }
            })
        }
    }

    private fun setupCreateBookingButton() {
        binding.btnCreateBooking.setOnClickListener {
            navigateToBookingActivity()
        }
    }

    private fun navigateToBookingActivity() {
        val intent = Intent(requireContext(), BookingActivity::class.java)
        startActivity(intent)
    }

    private fun setupFilterSection() {
        // Toggle filter visibility
        binding.btnFilter.setOnClickListener {
            toggleFilterVisibility()
        }

        // Clear filter button
        binding.btnClearFilter.setOnClickListener {
            clearAllFilters()
        }

        // Status chips
        setupStatusSpinner()
        // Setup Spinners với adapter rỗng ban đầu
        setupVehicleSpinner()
        setupBranchSpinner()
    }

    private fun clearAllFilters() {
        // Clear status chips
        binding.spinnerStatus.setSelection(0)

        // Reset vehicle spinner to "All"
        binding.spinnerVehicle.setSelection(0)

        // Reset branch spinner to "All"
        binding.spinnerBranch.setSelection(0)

        // Clear all filters in ViewModel
        viewModel.clearAllFilters()

        Toast.makeText(requireContext(), "All filters cleared", Toast.LENGTH_SHORT).show()
    }

    private fun setupStatusSpinner() {
        // Tạo adapter với các trạng thái
        val statusAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.repair_status_array,
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinnerStatus.adapter = statusAdapter

        binding.spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Chỉ filter khi position > 0 (không phải "All Status")
                val status = when (position) {
                    1 -> 0 // Pending
                    2 -> 1 // Accepted
                    3 -> 2 // Arrived
                    4 -> 3 // Cancelled
                    else -> null // All Status
                }
                viewModel.filterByStatus(status)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Không làm gì
            }
        }
    }

    private fun setupVehicleSpinner() {
        // Tạo adapter với mảng rỗng ban đầu
        val adapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            add("All") // Thêm item mặc định
        }

        binding.spinnerVehicle.adapter = adapter

        binding.spinnerVehicle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Chỉ filter khi position > 0 (không phải "All Vehicles")
                if (position > 0) {
                    val vehicleId = viewModel.vehicles.value.getOrNull(position - 1)?.vehicleID
                    viewModel.filterByVehicle(vehicleId)
                } else {
                    viewModel.filterByVehicle(null)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Không làm gì
            }
        }
    }

    private fun setupBranchSpinner() {
        // Tạo adapter với mảng rỗng ban đầu
        val adapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            add("All") // Thêm item mặc định
        }

        binding.spinnerBranch.adapter = adapter

        binding.spinnerBranch.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Chỉ filter khi position > 0 (không phải "All Branches")
                if (position > 0) {
                    val branchId = viewModel.branches.value.getOrNull(position - 1)?.branchId
                    viewModel.filterByBranch(branchId)
                } else {
                    viewModel.filterByBranch(null)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Không làm gì
            }
        }
    }

    private fun toggleFilterVisibility() {
        isFilterVisible = !isFilterVisible
        binding.cardFilter.visibility = if (isFilterVisible) View.VISIBLE else View.GONE

        val icon = if (isFilterVisible) R.drawable.ic_ft_expand_less else R.drawable.ic_filter
        binding.btnFilter.setImageResource(icon)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.repairRequests.collect { requests ->
                adapter.submitList(requests)
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.vehicles.collect { vehicles ->
                updateVehicleSpinner(vehicles)
                adapter.setVehicles(vehicles)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.branches.collect { branches ->
                updateBranchSpinner(branches)
                adapter.setBranches(branches)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                if (!isLoading) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun updateVehicleSpinner(vehicles: List<Vehicle>) {
        val adapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // Thêm item "All Vehicles" đầu tiên
        adapter.add("All")

        // Thêm các vehicles
        vehicles.forEach { vehicle ->
            adapter.add("${vehicle.brandName} ${vehicle.modelName} - ${vehicle.licensePlate}")
        }

        binding.spinnerVehicle.adapter = adapter
    }

    private fun updateBranchSpinner(branches: List<Branch>) {
        val adapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // Thêm item "All Branches" đầu tiên
        adapter.add("All")

        // Thêm các branches
        branches.forEach { branch ->
            adapter.add(branch.branchName)
        }

        binding.spinnerBranch.adapter = adapter
    }

    private fun showRepairRequestDetail(repairRequest: RepairRequest) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val detail = repository.getRepairRequestDetail(repairRequest.repairRequestID)
                Log.d("Detail",detail.toString())
                if (detail != null) {
                    RepairRequestDetailBottomSheet.newInstance(detail)
                        .show(parentFragmentManager, "RepairRequestDetail")
                } else {
                    Toast.makeText(requireContext(), "Failed to load details", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun updateRepairRequest(repairRequest: RepairRequest) {
        // Navigate to update screen
        Toast.makeText(requireContext(), "Update: ${repairRequest.repairRequestID}", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadInitialData()
    }
}