package com.example.garapro.ui.repairRequest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.databinding.FragmentVehicleSelectionBinding

class VehicleSelectionFragment : BaseBookingFragment() {

    private var _binding: FragmentVehicleSelectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var vehicleAdapter: VehicleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVehicleSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        vehicleAdapter = VehicleAdapter(emptyList()) { vehicle ->
            bookingViewModel.selectVehicle(vehicle)
            binding.tvSelectedVehicle.text = "${vehicle.brandName} ${vehicle.modelName} - ${vehicle.licensePlate}"
            binding.btnNext.isEnabled = true
        }


        binding.rvVehicles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = vehicleAdapter
        }
    }

    private fun setupObservers() {
        bookingViewModel.vehicles.observe(viewLifecycleOwner) { vehicles ->
            vehicleAdapter.updateData(vehicles)

            // Khi danh sách xe thay đổi, thử highlight lại xe đã chọn (nếu có)
            bookingViewModel.selectedVehicle.value?.let { selected ->
                val index = vehicleAdapter.getPositionOf(selected)
                if (index != RecyclerView.NO_POSITION) {
                    vehicleAdapter.setSelectedPosition(index)
                }
            }
        }

        bookingViewModel.selectedVehicle.observe(viewLifecycleOwner) { vehicle ->
            vehicle?.let {
                binding.tvSelectedVehicle.text = "${it.brandName} ${it.modelName} - ${it.licensePlate}"
                binding.btnNext.isEnabled = true
            }
        }
    }

    private fun setupListeners() {
        binding.btnNext.setOnClickListener {
            showNextFragment(R.id.action_vehicleSelection_to_branchSelection)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}