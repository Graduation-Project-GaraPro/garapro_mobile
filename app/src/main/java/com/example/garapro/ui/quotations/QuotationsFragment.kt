package com.example.garapro.ui.quotations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garapro.R
import com.example.garapro.data.model.quotations.Quotation
import com.example.garapro.data.model.quotations.QuotationStatus
import com.example.garapro.data.remote.RetrofitInstance
import com.example.garapro.data.repository.QuotationRepository
import com.example.garapro.databinding.FragmentQuotationsBinding
import com.google.android.material.snackbar.Snackbar

class QuotationsFragment : Fragment() {
    private var _binding: FragmentQuotationsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: QuotationListViewModel
    private var isFilterVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuotationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = QuotationRepository(RetrofitInstance.quotationService)
        viewModel = QuotationListViewModel(repository)

        setupUI()
        setupObservers()
    }

    private fun setupUI() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadQuotations()
        }

        // Toggle filter visibility
        binding.btnFilter.setOnClickListener {
            toggleFilterVisibility()
        }

        // Apply filter
        binding.btnApplyFilter.setOnClickListener {
            applyFilter()
            toggleFilterVisibility()
        }

        // Clear filter
        binding.btnClearFilter.setOnClickListener {
            clearFilter()
        }

        // Chip selection listener
        binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            updateChipAppearance()
        }
    }

    private fun toggleFilterVisibility() {
        isFilterVisible = !isFilterVisible

        if (isFilterVisible) {

            binding.cardFilter.visibility = View.VISIBLE
            binding.btnFilter.setImageResource(R.drawable.ic_ft_expand_less) // ↑ icon thu gọn
        } else {

            binding.cardFilter.visibility = View.GONE
            binding.btnFilter.setImageResource(R.drawable.ic_filter) // ⊞ icon bộ lọc
        }
    }


    private fun applyFilter() {
        val selectedChipId = binding.chipGroup.checkedChipId
        val status = when (selectedChipId) {
            binding.chipAll.id -> null
            binding.chipPending.id -> QuotationStatus.Pending
            binding.chipSent.id -> QuotationStatus.Sent
            binding.chipApproved.id -> QuotationStatus.Approved
            binding.chipRejected.id -> QuotationStatus.Rejected
            binding.chipExpired.id -> QuotationStatus.Expired
            else -> null
        }
        viewModel.filterByStatus(status)
    }

    private fun clearFilter() {
        binding.chipAll.isChecked = true
        viewModel.filterByStatus(null)
    }

    private fun updateChipAppearance() {
        // Tất cả các chip sẽ được tự động cập nhật màu theo Material Design 3
        // Không cần xử lý thủ công vì đã dùng style Chip.Filter
    }

    private fun setupObservers() {
        viewModel.quotations.observe(viewLifecycleOwner) { quotations ->
            binding.swipeRefresh.isRefreshing = false
            if (quotations.isNullOrEmpty()) {
                showEmptyState()
            } else {
                showQuotations(quotations)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading == true) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.selectedStatus.observe(viewLifecycleOwner) { status ->
            updateSelectedChip(status)
        }
    }

    private fun updateSelectedChip(status: QuotationStatus?) {
        val chipId = when (status) {
            null -> binding.chipAll.id
            QuotationStatus.Pending -> binding.chipPending.id
            QuotationStatus.Sent -> binding.chipSent.id
            QuotationStatus.Approved -> binding.chipApproved.id
            QuotationStatus.Rejected -> binding.chipRejected.id
            QuotationStatus.Expired -> binding.chipExpired.id
        }
        binding.chipGroup.check(chipId)
    }

    private fun showEmptyState() {
        binding.emptyState.root.visibility = View.VISIBLE
        binding.rvQuotations.visibility = View.GONE
    }

    private fun showQuotations(quotations: List<Quotation>) {
        binding.emptyState.root.visibility = View.GONE
        binding.rvQuotations.visibility = View.VISIBLE

        val adapter = QuotationAdapter(quotations) { quotation ->
            val bundle = Bundle().apply {
                putString("quotationId", quotation.quotationId)
            }
            findNavController().navigate(
                R.id.action_global_quotationDetailFragment,
                bundle
            )
        }
        binding.rvQuotations.adapter = adapter
        binding.rvQuotations.layoutManager = LinearLayoutManager(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}