package com.example.garapro.ui.repairRequest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.Service
import com.example.garapro.databinding.FragmentServiceBookingPartsSelectionBinding
import com.example.garapro.utils.MoneyUtils

class ServicePartsSelectionFragment : BaseBookingFragment() {

    private var _binding: FragmentServiceBookingPartsSelectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ServicePartsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentServiceBookingPartsSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()

        displaySelectedServices()
    }

    private fun setupRecyclerView() {
        adapter = ServicePartsAdapter(
            services = emptyList(),
            onPartSelected = { service, part ->
                bookingViewModel.selectPartForService(service, part)

            },
            isPartSelected = { part ->
                bookingViewModel.isPartSelected(part)
            }
        )

        binding.rvServiceParts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ServicePartsSelectionFragment.adapter
        }
    }

    private fun setupObservers() {
        bookingViewModel.selectedServices.observe(viewLifecycleOwner) { services ->
            adapter.updateData(services)
            updateSelectedServicesCount(services)
            updateTotalPrice()
        }

        bookingViewModel.selectedParts.observe(viewLifecycleOwner) { parts ->
            updateTotalPrice()
            // QUAN TRỌNG: Update adapter khi parts thay đổi
            val currentServices = bookingViewModel.selectedServices.value ?: emptyList()
            adapter.updateData(currentServices)
        }
    }

    private fun updateSelectedServicesCount(services: List<Service>) {
//        binding.tvSelectedServices.text = "${services.size} dịch vụ: ${
//            services.joinToString(", ") { it.serviceName }
//        }"
    }

    private fun displaySelectedServices() {
        val selectedServices = bookingViewModel.selectedServices.value ?: emptyList()
        adapter.updateData(selectedServices)
        updateTotalPrice()
    }

    private fun updateTotalPrice() {
        val totalPrice = bookingViewModel.calculateTotalPrice()
        binding.tvTotalPrice.text = "Tổng tiền: ${MoneyUtils.formatVietnameseCurrency(totalPrice)}"
    }

    private fun setupListeners() {
        binding.btnPrevious.setOnClickListener {
            showPreviousFragment()
        }

        binding.btnNext.setOnClickListener {
            showNextFragment(R.id.action_servicePartsSelection_to_details)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}