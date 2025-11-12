package com.example.garapro.ui.repairRequest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.ServiceCategory
import com.example.garapro.databinding.FragmentServiceSelectionBinding
import com.example.garapro.utils.MoneyUtils

class ServiceSelectionFragment : BaseBookingFragment() {

    private var _binding: FragmentServiceSelectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var serviceAdapter: ServiceAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentServiceSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()

        // Load mock data nếu cần
//        loadServiceData()
    }



    private fun setupRecyclerView() {
        serviceAdapter = ServiceAdapter(
            emptyList(),
            onServiceSelected = { service ->
                bookingViewModel.toggleServiceSelection(service)
                serviceAdapter.updateData(serviceAdapter.serviceCategories)
            },
            onPartSelected = { service, part ->
                bookingViewModel.selectPartForService(service, part)
                // Refresh để cập nhật UI ngay lập tức
                serviceAdapter.updateData(serviceAdapter.serviceCategories)
            },
            isServiceSelected = { service ->
                bookingViewModel.isServiceSelected(service)
            },
            isPartSelected = { part ->
                bookingViewModel.isPartSelected(part)
            }
        )

        binding.rvServices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = serviceAdapter
        }
    }

    private fun setupObservers() {
        bookingViewModel.serviceCategories.observe(viewLifecycleOwner) { categories ->
            serviceAdapter.updateData(categories)
        }

        bookingViewModel.selectedServices.observe(viewLifecycleOwner) { services ->
            updateUI()
        }

        bookingViewModel.selectedParts.observe(viewLifecycleOwner) { parts ->
            updateUI()
        }
    }

    private fun updateUI() {
        val selectedServices = bookingViewModel.selectedServices.value ?: emptyList()
        val selectedCount = selectedServices.size

        binding.tvSelectedServicesCount.text = "Đã chọn: $selectedCount dịch vụ"

        // Tính tổng tiền từ ViewModel
        val totalPrice = bookingViewModel.calculateTotalPrice()
        binding.tvTotalPrice.text = "Tổng tiền: ${MoneyUtils.formatVietnameseCurrency(totalPrice)}"

        updateNextButtonState()
    }

    private fun updateNextButtonState() {
        val hasServices = bookingViewModel.selectedServices.value?.isNotEmpty() == true
//        binding.btnNext.isEnabled = hasServices

        if (hasServices) {
            bookingViewModel.updateServiceSelection(true)
        }
    }

    private fun setupListeners() {
        binding.btnNext.setOnClickListener {
//            if (bookingViewModel.selectedServices.value?.isNotEmpty() == true) {
//                showNextFragment(R.id.action_serviceSelection_to_details)
//            } else {
//                Toast.makeText(requireContext(), "Vui lòng chọn ít nhất một dịch vụ", Toast.LENGTH_SHORT).show()
//            }

            showNextFragment(R.id.action_serviceSelection_to_details)
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