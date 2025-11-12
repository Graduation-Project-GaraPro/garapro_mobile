package com.example.garapro.ui.emergency_technician

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garapro.databinding.FragmentTechnicianEmergencyBinding

class TechnicianEmergencyFragment : Fragment() {

    private lateinit var binding: FragmentTechnicianEmergencyBinding
    private lateinit var viewModel: TechnicianViewModel
    private lateinit var adapter: EmergencyAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTechnicianEmergencyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[TechnicianViewModel::class.java]
        setupRecyclerView()
        setupObservers()
        setupClickListeners()

        viewModel.loadPendingEmergencies()
    }

    private fun setupRecyclerView() {
        adapter = EmergencyAdapter { emergency ->
            viewModel.acceptEmergency(emergency.id)
        }
        binding.rvEmergencies.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEmergencies.adapter = adapter
    }

    private fun setupObservers() {
        // Observe list of pending emergencies
        viewModel.pendingEmergencies.observe(viewLifecycleOwner) { emergencies ->
            Log.d("Technician", "Emergencies updated: ${emergencies.size} items")
            adapter.submitList(emergencies)

            if (emergencies.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.rvEmergencies.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.rvEmergencies.visibility = View.VISIBLE
            }
        }

        // Observe accepted emergency
        viewModel.acceptedEmergency.observe(viewLifecycleOwner) { emergency ->
            emergency?.let {
                binding.cardCurrentOrder.visibility = View.VISIBLE
                binding.tvCurrentOrder.text = "Đang xử lý đơn #${it.id.takeLast(4)}"

                // Reload pending emergencies (hide accepted one)
                viewModel.loadPendingEmergencies()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnRefresh.setOnClickListener {
            viewModel.loadPendingEmergencies()
        }

        binding.btnCompleteOrder.setOnClickListener {
            binding.cardCurrentOrder.visibility = View.GONE
//            viewModel.completeCurrentOrder()
        }
    }
}