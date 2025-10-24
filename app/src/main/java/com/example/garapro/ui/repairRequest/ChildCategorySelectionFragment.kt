package com.example.garapro.ui.repairRequest

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.Service
import com.example.garapro.data.model.repairRequest.ServiceCategory
import com.example.garapro.databinding.FragmentChildBookingCategorySelectionBinding
import com.google.android.material.chip.Chip
import android.text.TextWatcher
import android.util.Log

class ChildCategorySelectionFragment : BaseBookingFragment() {

    private var _binding: FragmentChildBookingCategorySelectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var serviceAdapter: ServiceSimpleAdapter
    private lateinit var filterAdapter: FilterChipAdapter

    // State management
    private var isFilterExpanded = false
    private var currentFilterCategoryId: String? = null
    private var currentFilterCategoryName: String = "Tất cả"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChildBookingCategorySelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Khôi phục filter state từ ViewModel
        restoreFilterState()

        setupAdapters()
        setupObservers()
        setupListeners()
        setupSearch()

        applyFilterState()

        Log.d("FilterDebug", "onViewCreated - currentFilter: $currentFilterCategoryId, name: $currentFilterCategoryName")
    }
    override fun onResume() {
        super.onResume()
        // Đảm bảo filter được áp dụng khi quay lại fragment
        applyCurrentFilter()
    }

    private fun restoreFilterState() {
        val savedFilterState = bookingViewModel.getChildFilterState()
        if (savedFilterState != null) {
            currentFilterCategoryId = savedFilterState.categoryId
            currentFilterCategoryName = savedFilterState.categoryName

            Log.d("FilterDebug", "Restored filter - id: $currentFilterCategoryId, name: $currentFilterCategoryName")

            // Khôi phục search term
            savedFilterState.searchTerm?.let { searchTerm ->
                binding.etSearch.setText(searchTerm)
                Log.d("FilterDebug", "Restored search term: $searchTerm")
            }
        } else {
            Log.d("FilterDebug", "No saved filter state")
        }
    }
    private fun applyCurrentFilter() {
        Log.d("FilterDebug", "Applying current filter - id: $currentFilterCategoryId")

        // Load data với filter hiện tại
        loadChildCategories(
            childServiceCategoryId = currentFilterCategoryId,
            searchTerm = bookingViewModel.getChildFilterState()?.searchTerm
        )

        // Update UI
        updateActiveFilterDisplay()
        updateFilterButtonUI()

        // Update filter adapter nếu có data
        bookingViewModel.allChildCategories.value?.let { categories ->
            filterAdapter.updateData(categories, currentFilterCategoryId)
        }
    }
    private fun setupAdapters() {
        // Service Adapter
        serviceAdapter = ServiceSimpleAdapter(
            services = emptyList(),
            onServiceSelected = { service ->
                bookingViewModel.toggleServiceSelection(service)
            },
            isServiceSelected = { service ->
                bookingViewModel.isServiceSelected(service)
            }
        )

        // Filter Adapter
        filterAdapter = FilterChipAdapter(
            categories = emptyList(),
            currentFilterCategoryId = currentFilterCategoryId,
            onFilterSelected = { categoryId ->
                onFilterCategorySelected(categoryId)
            }
        )

        binding.rvChildCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = serviceAdapter
        }

        binding.rvFilterChips.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = filterAdapter
        }
    }

    private fun onFilterCategorySelected(categoryId: String?) {
        Log.d("FilterDebug", "Filter category selected: $categoryId")

        currentFilterCategoryId = categoryId
        currentFilterCategoryName = getCategoryNameById(categoryId)

        // Lưu filter state vào ViewModel
        saveFilterState()

        updateActiveFilterDisplay()
        updateFilterButtonUI()
        loadChildCategories(childServiceCategoryId = categoryId)

        // QUAN TRỌNG: Update adapter với dữ liệu mới
        val currentCategories = bookingViewModel.allChildCategories.value ?: emptyList()
        filterAdapter.updateData(currentCategories, currentFilterCategoryId)
        // Update filter adapter để hiển thị chip được chọn
//        bookingViewModel.allChildCategories.value?.let { categories ->
//            filterAdapter.updateData(categories, currentFilterCategoryId)
//        }
    }

    private fun getCategoryNameById(categoryId: String?): String {
        return if (categoryId == null) {
            "Tất cả"
        } else {
            // Lấy từ allChildCategories thay vì filterAdapter.categories
            bookingViewModel.allChildCategories.value?.find { it.serviceCategoryId == categoryId }?.categoryName ?: "Tất cả"
        }
    }

    private fun setupObservers() {
        // Sử dụng allChildCategories thay vì childServiceCategories cho filter
        bookingViewModel.allChildCategories.observe(viewLifecycleOwner) { categories ->
            Log.d("FilterDebug", "All categories updated - ${categories.size} categories")

            // QUAN TRỌNG: Update filter adapter với current filter
            filterAdapter.updateData(categories, currentFilterCategoryId)

            // Nếu có filter đang active, đảm bảo nó được chọn trong UI
            if (currentFilterCategoryId != null) {
                updateActiveFilterDisplay()
            }
        }

        bookingViewModel.childServiceCategories.observe(viewLifecycleOwner) { response ->
            Log.d("FilterDebug", "Filtered services updated - ${response.data.flatMap { it.services }.size} services")
            serviceAdapter.updateData(response.data.flatMap { it.services })
        }

        bookingViewModel.selectedServices.observe(viewLifecycleOwner) { services ->
            updateSelectedServicesUI(services)
        }

        bookingViewModel.selectedParentCategory.observe(viewLifecycleOwner) { parent ->
            parent?.let {
                binding.tvSelectedParent.text = "Danh mục: ${it.categoryName}"
            }
        }
    }

    private fun updateSelectedServicesUI(services: List<Service>) {
        binding.tvSelectedCount.text = "Đã chọn: ${services.size} dịch vụ"

        updateSelectedServicesPreview(services)
    }

    private fun updateSelectedServicesPreview(services: List<Service>) {
        val container = binding.containerSelectedPreview
        container.removeAllViews()

        if (services.isNotEmpty()) {
            container.visibility = View.VISIBLE
            // Your existing preview code here
        } else {
            container.visibility = View.GONE
        }
    }

    private fun applyFilterState() {
        binding.cardFilter.visibility = if (isFilterExpanded) View.VISIBLE else View.GONE
        updateFilterButtonUI()
    }
    private fun saveFilterState(searchTerm: String? = null) {
        val currentSearchTerm = searchTerm ?: binding.etSearch.text?.toString()?.trim()

        val filterState = BookingViewModel.ChildFilterState(
            categoryId = currentFilterCategoryId,
            categoryName = currentFilterCategoryName,
            searchTerm = if (currentSearchTerm.isNullOrEmpty()) null else currentSearchTerm
        )
        bookingViewModel.setChildFilterState(filterState)

        Log.d("FilterDebug", "Filter state saved - id: $currentFilterCategoryId, name: $currentFilterCategoryName, search: $currentSearchTerm")
    }

    private fun clearFilter() {
        currentFilterCategoryId = null
        currentFilterCategoryName = "Tất cả"

        // Clear search
        binding.etSearch.setText("")

        // Update filter adapter
        bookingViewModel.allChildCategories.value?.let { categories ->
            filterAdapter.updateData(categories, null)
        }

        // Lưu state cleared
        saveFilterState(searchTerm = null)

        updateActiveFilterDisplay()
        updateFilterButtonUI()
        loadChildCategories(childServiceCategoryId = null)

        Log.d("FilterDebug", "Filter cleared")
    }
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            private var searchRunnable: Runnable? = null
            private val handler = Handler(Looper.getMainLooper())

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(editable: Editable?) {
                searchRunnable?.let { handler.removeCallbacks(it) }

                searchRunnable = Runnable {
                    val searchTerm = editable?.toString()?.trim()

                    // Lưu search term vào ViewModel
                    saveFilterState(searchTerm = if (searchTerm.isNullOrEmpty()) null else searchTerm)

                    loadChildCategories(
                        childServiceCategoryId = currentFilterCategoryId,
                        searchTerm = searchTerm
                    )
                }

                handler.postDelayed(searchRunnable!!, 500)
            }
        })
    }

    private fun setupListeners() {
        binding.btnToggleFilter.setOnClickListener {
            toggleFilterVisibility()
        }

        binding.btnPrevious.setOnClickListener {
            showPreviousFragment()
        }

        binding.btnNext.setOnClickListener {
            val selectedServices = bookingViewModel.selectedServices.value ?: emptyList()

            if (selectedServices.isEmpty()) {
                // KHÔNG chọn service nào -> đi thẳng đến details
                Log.d("Navigation", "No services selected, going directly to details")
                showNextFragment(R.id.action_childCategorySelection_to_details)
            } else {
                // CÓ chọn service -> đi đến parts selection
                Log.d("Navigation", "${selectedServices.size} services selected, going to parts selection")
                showNextFragment(R.id.action_childCategorySelection_to_servicePartsSelection)
            }
        }
        // Enable nút Next ngay từ đầu
        binding.btnNext.isEnabled = true
    }

    private fun toggleFilterVisibility() {
        isFilterExpanded = !isFilterExpanded
        binding.cardFilter.visibility = if (isFilterExpanded) View.VISIBLE else View.GONE
        updateFilterButtonUI()
    }

    private fun updateFilterButtonUI() {
        val buttonText = when {
            isFilterExpanded -> "Đóng lọc"
            currentFilterCategoryId != null -> "Bộ lọc •"
            else -> "Bộ lọc"
        }

        val iconRes = if (isFilterExpanded) R.drawable.ic_expand_less else R.drawable.ic_filter

        binding.btnToggleFilter.text = buttonText
        binding.btnToggleFilter.setIconResource(iconRes)
    }

    private fun updateActiveFilterDisplay() {
        val container = binding.containerActiveFilter

        if (currentFilterCategoryId != null) {
            binding.chipActiveFilter.text = currentFilterCategoryName
            container.visibility = View.VISIBLE
            Log.d("FilterDebug", "Active filter displayed: $currentFilterCategoryName")
        } else {
            container.visibility = View.GONE
            Log.d("FilterDebug", "No active filter to display")
        }

        binding.chipActiveFilter.setOnCloseIconClickListener {
            clearFilter()
        }
    }



    private fun loadChildCategories(
        childServiceCategoryId: String? = null,
        searchTerm: String? = null
    ) {
        val parentId = bookingViewModel.selectedParentCategory.value?.serviceCategoryId
        parentId?.let {
            bookingViewModel.loadChildServiceCategories(
                parentId = it,
                childServiceCategoryId = childServiceCategoryId,
                searchTerm = searchTerm
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("FILTER_EXPANDED", isFilterExpanded)
        outState.putString("CURRENT_FILTER_ID", currentFilterCategoryId)
        outState.putString("CURRENT_FILTER_NAME", currentFilterCategoryName)
    }

    override fun onDestroyView() {
        saveFilterState()
        super.onDestroyView()
        _binding = null
    }
}