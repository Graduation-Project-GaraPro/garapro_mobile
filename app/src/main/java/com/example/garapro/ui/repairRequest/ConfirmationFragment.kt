package com.example.garapro.ui.repairRequest

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.Service
import com.example.garapro.databinding.FragmentBookingConfirmationBinding
import com.example.garapro.utils.MoneyUtils

class ConfirmationFragment : BaseBookingFragment() {

    private var _binding: FragmentBookingConfirmationBinding? = null
    private val binding get() = _binding!!
    private lateinit var imageAdapter: ImageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBookingConfirmationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()
        displayConfirmationData()
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(mutableListOf(), { /* do nothing */ }, isReadOnly = true)

        binding.rvConfirmationImages.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = imageAdapter
        }
    }

    private fun setupObservers() {
        bookingViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
//            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSubmit.isEnabled = !isLoading
        }

        bookingViewModel.submitResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                if (it) {
                    showSuccessDialog()
                } else {
                    showErrorDialog()
                }
                bookingViewModel.resetSubmitResult()
            }
        }
    }

    private fun displayConfirmationData() {
        // Vehicle
        bookingViewModel.selectedVehicle.value?.let { vehicle ->
            binding.tvVehicle.text = "${vehicle.brandName} ${vehicle.modelName}\nBiển số: ${vehicle.licensePlate}\nNăm sản xuất: ${vehicle.year}"
        }

        // Branch
        bookingViewModel.selectedBranch.value?.let { branch ->
            binding.tvBranch.text = "${branch.branchName}\n${branch.district}, ${branch.city}\nĐiện thoại: ${branch.phoneNumber}"
        }

        // Services
        displayServicesDetails()

        // Date
        bookingViewModel.requestDate.value?.let { date ->
            binding.tvDate.text = date
        }

        // Description
        bookingViewModel.description.value?.let { description ->
            binding.tvDescription.text = description
        }

        // Images
        bookingViewModel.imageUris.value?.let { images ->
            binding.tvImages.text = "Số ảnh: ${images.size}"
            imageAdapter.updateData(images.toMutableList())
        }

        // Total Price
        val totalPrice = bookingViewModel.calculateTotalPrice()
        binding.tvTotalPrice.text = "Tổng tiền: ${MoneyUtils.formatVietnameseCurrency(totalPrice)}"
    }

    private fun displayServicesDetails() {
        val container = binding.containerServices
        container.removeAllViews()

        bookingViewModel.selectedServices.value?.forEach { service ->
            // Service item
            val serviceView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_confirmation_service, container, false)

            val tvServiceName = serviceView.findViewById<TextView>(R.id.tvServiceName)
            val tvServicePrice = serviceView.findViewById<TextView>(R.id.tvServicePrice)
            val containerParts = serviceView.findViewById<LinearLayout>(R.id.containerParts)

            val servicePrice = MoneyUtils.calculateServicePrice(service)
            tvServiceName.text = service.serviceName
            tvServicePrice.text = MoneyUtils.formatVietnameseCurrency(servicePrice)

            // Display selected parts for this service
            displaySelectedParts(service, containerParts)

            container.addView(serviceView)

            // Add divider between services
            if (service != bookingViewModel.selectedServices.value?.last()) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        setMargins(0, 8, 0, 8)
                    }
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))
                }
                container.addView(divider)
            }
        }
    }

    private fun displaySelectedParts(service: Service, container: LinearLayout) {
        container.removeAllViews()

        val selectedParts = bookingViewModel.selectedParts.value?.values?.filter { part ->
            service.partCategories.any { category ->
                category.parts.any { p -> p.partId == part.partId }
            }
        } ?: emptyList()

        if (selectedParts.isNotEmpty()) {
            selectedParts.forEach { part ->
                val partView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_confirmation_part, container, false)

                val tvPartName = partView.findViewById<TextView>(R.id.tvPartName)
                val tvPartPrice = partView.findViewById<TextView>(R.id.tvPartPrice)

                tvPartName.text = part.name
                tvPartPrice.text = MoneyUtils.formatVietnameseCurrency(part.price)

                container.addView(partView)
            }
        }
    }

    private fun setupListeners() {
        binding.btnSubmit.setOnClickListener {
            bookingViewModel.submitRepairRequest()
        }

        binding.btnEdit.setOnClickListener {
            // Quay lại step đầu tiên để chỉnh sửa
            findNavController().popBackStack(R.id.vehicleSelectionFragment, false)
        }
    }

    private fun showSuccessDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Thành Công")
            .setMessage("Đặt lịch thành công! Chúng tôi sẽ liên hệ với bạn sớm.")
            .setPositiveButton("OK") { _, _ ->
                requireActivity().finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showErrorDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Lỗi")
            .setMessage("Đặt lịch thất bại. Vui lòng thử lại.")
            .setPositiveButton("Thử lại", null)
            .setNegativeButton("Hủy") { _, _ ->
                requireActivity().finish()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}