package com.example.garapro.ui.repairRequest

import android.graphics.Color
import com.example.garapro.databinding.FragmentBranchSelectionBinding
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
class BranchSelectionFragment : BaseBookingFragment() {

    private var _binding: FragmentBranchSelectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var branchAdapter: BranchAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBranchSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        branchAdapter = BranchAdapter(emptyList()) { branch ->
            bookingViewModel.selectBranch(branch)
            binding.tvSelectedBranch.text = branch.branchName
            binding.btnNext.isEnabled = true
        }

        binding.rvBranches.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = branchAdapter
        }
    }

    private fun setupObservers() {
        // Quan sát danh sách chi nhánh
        bookingViewModel.branches.observe(viewLifecycleOwner) { branches ->
            branchAdapter.updateData(branches)

            // Khi danh sách branch được load, highlight lại chi nhánh đã chọn (nếu có)
            bookingViewModel.selectedBranch.value?.let { selected ->
                val index = branchAdapter.getPositionOf(selected)
                if (index != RecyclerView.NO_POSITION) {
                    branchAdapter.setSelectedPosition(index)
                }
            }
        }

        // Quan sát chi nhánh được chọn
        bookingViewModel.selectedBranch.observe(viewLifecycleOwner) { branch ->
            branch?.let {
                binding.tvSelectedBranch.text = it.branchName
                binding.btnNext.isEnabled = true
                binding.btnNext.setBackgroundColor(Color.BLACK)

            }
        }
    }

    private fun setupListeners() {
        binding.btnNext.setOnClickListener {
            showNextFragment(R.id.action_branchSelection_to_parentCategorySelection)
        }

        binding.btnPrevious.setOnClickListener {
            showPreviousFragment()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}