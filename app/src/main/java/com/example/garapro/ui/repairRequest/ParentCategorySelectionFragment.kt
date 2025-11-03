package com.example.garapro.ui.repairRequest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garapro.R
import com.example.garapro.databinding.FragmentParentBookingCategorySelectionBinding

class ParentCategorySelectionFragment : BaseBookingFragment() {

    private var _binding: FragmentParentBookingCategorySelectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ParentCategoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentBookingCategorySelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()

        bookingViewModel.loadParentServiceCategories()
    }

    private fun setupRecyclerView() {
        adapter = ParentCategoryAdapter(emptyList()) { category ->
            bookingViewModel.selectParentCategory(category)
            showNextFragment(R.id.action_parentCategorySelection_to_childCategorySelection)
        }

        binding.rvParentCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ParentCategorySelectionFragment.adapter
        }
    }

    private fun setupObservers() {
        bookingViewModel.parentServiceCategories.observe(viewLifecycleOwner) { categories ->
            adapter.updateData(categories)
        }
    }

    private fun setupListeners() {
        binding.btnPrevious.setOnClickListener {
            showPreviousFragment()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}