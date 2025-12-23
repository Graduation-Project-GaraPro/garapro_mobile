package com.example.garapro.ui.RepairProgress.archived

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.RepairProgresses.PagedResult
import com.example.garapro.data.model.RepairProgresses.RepairOrderArchivedListItem
import com.example.garapro.data.repository.RepairProgress.RepairProgressRepository
import com.example.garapro.databinding.FragmentRepairOrderArchivedListBinding
import com.example.garapro.hubs.RepairOrderArchiveHubService
import com.example.garapro.utils.Constants
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

class RepairOrderArchivedListFragment : Fragment() {

    private var _binding: FragmentRepairOrderArchivedListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RepairOrderArchivedAdapter
    private var archiveHubService: RepairOrderArchiveHubService? = null

    private val viewModel: RepairOrderArchivedListViewModel by viewModels {
        RepairOrderArchivedListViewModelFactory()
    }

    companion object {
        private const val PREFS_AUTH = "auth_prefs"
        private const val KEY_USER_ID = "user_id"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepairOrderArchivedListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupFilter()
        observeViewModel()

        initRepairOrderHub()
        observeRepairOrderHubEvents()

        viewModel.loadOrders()
    }

    private fun setupToolbar() {
        // Bỏ nút back
        binding.toolbar.navigationIcon = null
        binding.toolbar.setNavigationOnClickListener(null)

        // (tuỳ chọn) nếu toolbar bị chừa khoảng inset bên trái
        binding.toolbar.setContentInsetsAbsolute(0, 0)
    }

    private fun setupRecyclerView() {
        adapter = RepairOrderArchivedAdapter { item -> openDetail(item) }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) return
                if (!recyclerView.canScrollVertically(1)) {
                    viewModel.loadNextPage()
                }
            }
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun setupFilter() {
        binding.filterButton.setOnClickListener {
            binding.filterContainer.visibility =
                if (binding.filterContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.dateFilter.setOnClickListener {
            showDateRangePicker()
        }

        binding.clearFilterButton.setOnClickListener {
            viewModel.clearFilter()
            binding.dateFilter.text = getString(R.string.select_date_range)
        }
    }

    private fun showDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.select_date_range)
            .setTheme(R.style.ThemeOverlay_GaraPro_DatePicker)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val start = selection.first
            val end = selection.second

            if (start != null && end != null) {
                val apiFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                val cal = Calendar.getInstance()

                cal.timeInMillis = start
                val fromApi = apiFormat.format(cal.time)
                val fromDisplay = displayFormat.format(cal.time)

                cal.timeInMillis = end
                val toApi = apiFormat.format(cal.time)
                val toDisplay = displayFormat.format(cal.time)

                viewModel.updateDateRangeFilter(fromApi, toApi)
                binding.dateFilter.text = "$fromDisplay - $toDisplay"
            }
        }

        picker.show(parentFragmentManager, "archived_date_range_picker")
    }

    private fun initRepairOrderHub() {
        val hubUrl = Constants.BASE_URL_SIGNALR + "/api/archivehub"

        val prefs = requireContext().getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
        val userId = prefs.getString(KEY_USER_ID, null) ?: return

        archiveHubService = RepairOrderArchiveHubService(hubUrl).apply {
            setupListeners()
            connectAndJoin(userId)
        }
    }

    private fun observeRepairOrderHubEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            archiveHubService?.events?.collect {
                Log.d("ArchivedHub", "Received Archive event")
                viewModel.refresh()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.ordersState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RepairProgressRepository.ApiResponse.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                }

                is RepairProgressRepository.ApiResponse.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    applyData(state.data)
                }

                is RepairProgressRepository.ApiResponse.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    binding.emptyState.visibility = View.VISIBLE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoadingPage.collect { isLoading ->
                binding.loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
    }

    private fun applyData(paged: PagedResult<RepairOrderArchivedListItem>) {
        val items = paged.items ?: emptyList()
        binding.emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        adapter.submitList(items)
    }

    private fun openDetail(item: RepairOrderArchivedListItem) {
        val bundle = bundleOf("repairOrderId" to item.repairOrderId)
        findNavController().navigate(R.id.repairArchivedDetailFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { archiveHubService?.leaveAndStop() } catch (_: Exception) {}
        archiveHubService = null
        _binding = null
    }
}
