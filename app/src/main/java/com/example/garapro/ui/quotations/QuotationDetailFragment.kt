package com.example.garapro.ui.quotations

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garapro.R
import com.example.garapro.data.model.quotations.CustomerPromotion
import com.example.garapro.data.model.quotations.CustomerPromotionResponse
import com.example.garapro.data.model.quotations.Quotation
import com.example.garapro.data.model.quotations.QuotationDetail
import com.example.garapro.data.model.quotations.QuotationService
import com.example.garapro.data.model.quotations.QuotationStatus
import com.example.garapro.data.model.quotations.SubmitConfirmationType
import com.example.garapro.data.remote.RetrofitInstance
import com.example.garapro.data.repository.QuotationRepository
import com.example.garapro.databinding.FragmentQuotationDetailBinding
import com.example.garapro.ui.promotions.PromotionBottomSheetFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.util.Locale

class QuotationDetailFragment : Fragment() {
    private var _binding: FragmentQuotationDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QuotationDetailViewModel by lazy {
        QuotationDetailViewModel(QuotationRepository(RetrofitInstance.quotationService))
    }

    private val quotationId by lazy {
        arguments?.getString("quotationId") ?: throw IllegalStateException("quotationId required")
    }
    private lateinit var adapter: QuotationServiceAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentQuotationDetailBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupObservers()
        viewModel.loadQuotation(quotationId)
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnSubmit.setOnClickListener { showSubmitConfirmation() }

        binding.btnCall.setOnClickListener {
            startCall()
        }
        binding.etCustomerNote.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val note = s?.toString() ?: ""
                viewModel.updateCustomerNote(note)
                updateNoteValidationUI(note)
            }
        })

        // Initialize adapter
        adapter = QuotationServiceAdapter(
            services = emptyList(),
            onCheckChanged = { id, checked -> viewModel.onServiceCheckChanged(id, checked) },
            onPartToggle = { serviceId, categoryId, partId ->
                viewModel.togglePartSelection(serviceId, categoryId, partId)
            },
            onPromotionClick = { service ->
                viewModel.onPromotionClick(service)
            }
        )

        binding.rvServices.adapter = adapter
        binding.rvServices.layoutManager = LinearLayoutManager(requireContext())
    }


    private fun startCall() {
        val phone = viewModel.quotation.value?.phoneBranch?.trim().orEmpty()

        Log.d("phone", phone)
        if (phone.isBlank()) {
            Snackbar.make(binding.root, "Phone number not available", Snackbar.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Snackbar.make(binding.root, "No dialer app found", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun updateNoteValidationUI(note: String) {
        val isValid = note.length >= 10

        if (note.isNotEmpty()) {
            if (isValid) {
                binding.tilCustomerNote.error = null
                binding.tilCustomerNote.helperText = "Entered ${note.length}/10 characters"
            } else {
                binding.tilCustomerNote.error = "Minimum 10 characters required"
                binding.tilCustomerNote.helperText = "Entered ${note.length}/10 characters"
            }
        } else {
            binding.tilCustomerNote.error = "Required when services are unselected"
            binding.tilCustomerNote.helperText = null
        }
    }

    private fun setupObservers() {
        viewModel.quotation.observe(viewLifecycleOwner) { quotation ->
            quotation?.let {
                setupQuotationDetails(it)
                setupUIBasedOnStatus(it.status)
                adapter.updateServices(it.quotationServices)
            }
        }

        viewModel.refreshAdapter.observe(viewLifecycleOwner) {
            viewModel.quotation.value?.let {
                adapter.updateServices(it.quotationServices)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) {
            binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE
        }

        viewModel.isSubmitting.observe(viewLifecycleOwner) {
            updateSubmitButton(it)
        }

        viewModel.submitSuccess.observe(viewLifecycleOwner) {
            if (it) onSubmitSuccess()
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) {
            it?.let(::showError)
        }

        viewModel.openPromotionDialog.observe(viewLifecycleOwner) { response ->
            response?.let {
                showPromotionBottomSheet(it)
                viewModel.clearOpenPromotionDialog()
            }
        }

        viewModel.servicePromotions.observe(viewLifecycleOwner) { map ->
            adapter.updatePromotions(map)
            calculateTotal()
        }

        viewModel.pendingServiceToggle.observe(viewLifecycleOwner) {
            it?.let(::showUnselectWarning)
        }

        viewModel.canSubmit.observe(viewLifecycleOwner) { canSubmit ->
            binding.btnSubmit.isEnabled = canSubmit && (viewModel.isSubmitting.value != true)
        }

        viewModel.hasUnselectedServices.observe(viewLifecycleOwner) { hasUnselected ->
            // Show customer note field when services are unselected
            if (hasUnselected)
            {
                binding.titleCustomerNote.text = "Note"
                binding.customerNoteSection.visibility = View.VISIBLE
            }else {
                binding.customerNoteSection.visibility = View.GONE
            }
        }
    }

    private fun setupUIBasedOnStatus(status: QuotationStatus) {

        val phoneBranch = viewModel.quotation.value?.phoneBranch?.trim().orEmpty()
        when (status) {
            QuotationStatus.Sent -> {
                // mode edit
                setupEditableMode()
                adapter.updateEditable(true)


                binding.btnCall.visibility = View.GONE
            }
            QuotationStatus.Good -> {

                setupReadOnlyMode(status)


                binding.tvReadOnlyNotice.visibility = View.GONE
                binding.tvEditNotice.visibility = View.VISIBLE
                binding.tvEditNotice.text =
                    "Your car is in good condition. You can come to pick up your car or contact us if you want to use additional services."

                binding.btnCall.visibility = if (phoneBranch.isNotEmpty()) View.VISIBLE else View.GONE


                adapter.updateEditable(false)
            }
            else -> {

                setupReadOnlyMode(status)
                adapter.updateEditable(false)


                binding.btnCall.visibility = View.GONE
            }
        }
    }


    private fun showPromotionBottomSheet(response: CustomerPromotionResponse) {
        try {
            val quotation = viewModel.quotation.value ?: run {
                Toast.makeText(requireContext(), "quotation null", Toast.LENGTH_SHORT).show()
                Log.d("PromotionSheet", "quotation null")
                return
            }

            val service = quotation.quotationServices
                .find { it.serviceId == response.serviceId }
                ?: run {
                    Toast.makeText(requireContext(), "service not found", Toast.LENGTH_SHORT).show()
                    Log.d("PromotionSheet", "service not found for id=${response.serviceId}")
                    return
                }

            val originalPrice = viewModel.calculateCurrentOrderValue(service)
            val currentState = viewModel.servicePromotions.value?.get(response.serviceId)
            val selectedPromotionId = currentState?.selectedPromotion?.id
            val eligiblePromotions = response.promotions.filter { it.isEligible }

            Log.d("PromotionSheet", "init, eligible=${eligiblePromotions.size}")

            if (eligiblePromotions.isEmpty()) {
                Toast.makeText(requireContext(), "No available promotion", Toast.LENGTH_SHORT).show()
                Log.d("PromotionSheet", "eligiblePromotions EMPTY")
                return
            }

            val bottomSheet = PromotionBottomSheetFragment.newInstance(
                serviceName = service.serviceName,
                originalPrice = originalPrice,
                promotions = eligiblePromotions,
                selectedPromotionId = selectedPromotionId
            )

            bottomSheet.setListener(object : PromotionBottomSheetFragment.PromotionSelectionListener {
                override fun onPromotionSelected(promotion: CustomerPromotion?, originalPrice: Double) {
                    viewModel.applyPromotion(response.serviceId, promotion, originalPrice)
                }

                override fun onPromotionRemoved(originalPrice: Double) {
                    viewModel.applyPromotion(response.serviceId, null, originalPrice)
                }
            })

            Log.d("PromotionSheet", "show() called")
            Toast.makeText(requireContext(), "Opening bottom sheet...", Toast.LENGTH_SHORT).show()

            bottomSheet.show(parentFragmentManager, "PromotionBottomSheet")

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error showing promotion sheet", Toast.LENGTH_SHORT).show()
            Log.e("PromotionSheet", "Error showing bottom sheet", e)
        }
    }


    private fun setupEditableMode() {
        // Allow checkbox clicks
        adapter.updateOnCheckChanged { id, checked ->
            viewModel.onServiceCheckChanged(id, checked)
        }

        // Show submit button
        binding.btnSubmit.visibility = View.VISIBLE
        binding.tvEditNotice.visibility = View.VISIBLE
        binding.tvReadOnlyNotice.visibility = View.GONE

        // Show selected total
        binding.tvSelectedTotal.visibility = View.VISIBLE

        binding.customerNoteSection.visibility = View.GONE

        binding.btnReject.visibility = View.VISIBLE
        binding.btnReject.setOnClickListener {
            showRejectConfirmation()
        }

        binding.btnCall.visibility = View.GONE

        calculateTotal()
    }

    private fun showRejectConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reject Quotation")
            .setMessage("Would you like to provide a reason for rejection?")
            .setPositiveButton("Yes, enter reason") { _, _ ->
                // Show customer note section and focus on input
                binding.customerNoteSection.visibility = View.VISIBLE
                binding.etCustomerNote.requestFocus()

                // Show reject button after entering reason
                setupRejectWithNoteMode()
            }
            .setNegativeButton("No") { _, _ ->
                viewModel.updateCustomerNote("")
                viewModel.rejectQuotation()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun setupRejectWithNoteMode() {
        binding.btnReject.text = "Submit rejection"
        binding.titleCustomerNote.text = "Note(*)"

        binding.btnReject.setOnClickListener {
            val note = binding.etCustomerNote.text?.toString().orEmpty().trim()


            viewModel.updateCustomerNote(note)

            if (note.length >= 10) {
                viewModel.rejectQuotation()
            } else {
                Snackbar.make(binding.root, "Please enter at least 10 characters", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupReadOnlyMode(status: QuotationStatus) {
        // Disable checkboxes - handled in adapter.updateEditable(false)
        adapter.updateOnCheckChanged { _, _ ->
            // Do nothing when clicked
            showReadOnlyMessage(status)
        }

        // Hide submit button
        binding.btnSubmit.visibility = View.GONE
        binding.tvEditNotice.visibility = View.GONE
        binding.tvReadOnlyNotice.visibility = View.VISIBLE

        // Hide selected total
        binding.tvlabelSelectedTotal.visibility = View.GONE
        binding.tvSelectedTotal.visibility = View.GONE

        binding.btnReject.visibility = View.GONE


        binding.btnCall.visibility = View.GONE

        // Hide customer note field
        val quotation = viewModel.quotation.value
        val hasNote = !viewModel.customerNote.value.isNullOrBlank()
        binding.customerNoteSection.visibility = if (hasNote) View.VISIBLE else View.GONE

        if (hasNote) {
            // Disable edit text and show note
            binding.etCustomerNote.isEnabled = false
            binding.etCustomerNote.setText(quotation?.customerNote.orEmpty())
            binding.tilCustomerNote.helperText = "Your note"
            binding.tilCustomerNote.boxBackgroundColor = ContextCompat.getColor(requireContext(), R.color.gray_light)
        } else {
            binding.customerNoteSection.visibility = View.VISIBLE
            binding.etCustomerNote.isEnabled = false
            binding.etCustomerNote.setText("No note")
            binding.tilCustomerNote.helperText = "No note"
            binding.tilCustomerNote.boxBackgroundColor = ContextCompat.getColor(requireContext(), R.color.gray_light)
        }

        binding.tvReadOnlyNotice.text = getReadOnlyMessage(status)
    }

    private fun showReadOnlyMessage(status: QuotationStatus) {
        val message = when (status) {
            QuotationStatus.Approved -> "Quotation has been approved, cannot be changed"
            QuotationStatus.Rejected -> "Quotation has been rejected, cannot be changed"
            QuotationStatus.Expired -> "Quotation has expired, cannot be changed"
            QuotationStatus.Pending -> "Quotation is pending, cannot respond yet"
            QuotationStatus.Good -> "Your car is in good condition"
            else -> "Cannot change quotation in current status"
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }



    private fun getReadOnlyMessage(status: QuotationStatus): String {
        return when (status) {
            QuotationStatus.Approved -> "Quotation has been approved"
            QuotationStatus.Rejected -> "Quotation has been rejected"
            QuotationStatus.Expired -> "Quotation has expired"
            QuotationStatus.Pending -> "Quotation is pending"
            else -> "View mode"
        }
    }

    private fun setupQuotationDetails(quotation: QuotationDetail) {
        binding.tvVehicleInfo.text = quotation.vehicleInfo
        binding.tvCustomerName.text = quotation.customerName
        binding.tvTotalAmount.text = formatCurrency(quotation.totalAmount)
        binding.tvStatus.text = getStatusText(quotation.status)
        binding.tvStatus.setTextColor(getStatusColor(quotation.status))

        if (!quotation.customerNote.isNullOrBlank() && quotation.status != QuotationStatus.Sent) {
            binding.etCustomerNote.setText(quotation.customerNote)
            binding.etCustomerNote.isEnabled = false
            binding.tilCustomerNote.helperText = "Customer note"
        }

        calculateTotal()
    }



    private fun calculateTotal() {
        val quotation = viewModel.quotation.value ?: return
        var total = 0.0

        quotation.quotationServices.forEach { service ->
            if (service.isSelected) {
                total += viewModel.getFinalPriceForService(service)
            }
        }

        binding.tvSelectedTotal.text = formatCurrency(total)
        updateSubmitButton(viewModel.isSubmitting.value ?: false)
    }

    private fun updateSubmitButton(isSubmitting: Boolean) {
        // Kiểm tra xem có service nào được chọn không
        val quotation = viewModel.quotation.value
        val hasSelectedServices = quotation?.quotationServices?.any { it.isSelected } == true

        // Nếu KHÔNG có service nào được chọn → ẩn nút Submit, chỉ còn Reject
        if (!hasSelectedServices) {
            binding.btnSubmit.visibility = View.GONE

            binding.btnReject.visibility = View.VISIBLE
            binding.btnReject.isEnabled = !isSubmitting
            // Có thể đổi text nếu muốn: binding.btnReject.text = "Reject"
            return
        } else {
            // Có ít nhất 1 service được chọn → hiện nút Submit như bình thường
            binding.btnSubmit.visibility = View.VISIBLE
            binding.btnReject.visibility = View.VISIBLE
        }

        // Phần còn lại giữ nguyên logic cũ
        val canSubmit = viewModel.canSubmit.value == true
        val isRejectMode = viewModel.isRejectMode.value == true

        binding.btnSubmit.isEnabled = canSubmit && !isSubmitting
        binding.btnReject.isEnabled = !isSubmitting

        val bgColorRes =
            if (!canSubmit || isSubmitting) {
                R.color.material_on_surface_disabled
            } else {
                if (isRejectMode) R.color.blue else R.color.green
            }

        binding.btnSubmit.setBackgroundColor(
            ContextCompat.getColor(requireContext(), bgColorRes)
        )

        binding.btnSubmit.text = when {
            isSubmitting -> "Submitting..."

            else -> "Accept"
        }
    }


    private fun showUnselectWarning(event: QuotationDetailViewModel.ServiceToggleEvent) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Unselection")
            .setMessage("Unselect service \"${event.serviceName}\"?")
            .setPositiveButton("Unselect") { _, _ ->
                viewModel.confirmServiceToggle(event.serviceId, event.currentChecked)
                calculateTotal()
            }
            .setNegativeButton("Keep") { _, _ ->
                viewModel.cancelServiceToggle()
            }
            .create()

        // IMPORTANT: Handle when dialog is dismissed
        dialog.setOnDismissListener {
            // If dialog is dismissed without selecting button, cancel toggle
            if (viewModel.pendingServiceToggle.value != null) {
                viewModel.cancelServiceToggle()
            }
        }

        dialog.show()
    }

    private fun showSubmitConfirmation() {
        val quotation = viewModel.quotation.value ?: return

        // CHECK VALIDATION - if fail, show only one notification and return
        val validationMessage = viewModel.getValidationMessage()
        if (validationMessage.isNotEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Missing Information")
                .setMessage(validationMessage)
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val customerNote = viewModel.customerNote.value
        val (title, message) = when (viewModel.getSubmitConfirmationType()) {
            SubmitConfirmationType.APPROVED -> {
                val totalAmount = calculateSelectedTotal(quotation)
                "Confirm Acceptance" to "You are accepting ALL services with total amount ${formatCurrency(totalAmount)}. Continue?"
            }
            SubmitConfirmationType.REJECTED -> {
                "Confirm Rejection" to "Are you sure you want to reject this quotation?"
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Confirm") { _, _ -> viewModel.submitCustomerResponse() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun calculateSelectedTotal(quotation: QuotationDetail): Double {
        var total = 0.0

        quotation.quotationServices.forEach { service ->
            if (service.isSelected) {
                total += viewModel.getFinalPriceForService(service)
            }
        }

        return total
    }

    private fun onSubmitSuccess() {
        Snackbar.make(binding.root, "Response submitted successfully", Snackbar.LENGTH_LONG).show()
        findNavController().navigateUp()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        viewModel.clearError()
    }

    private fun getStatusText(status: QuotationStatus): String {
        return when (status) {
            QuotationStatus.Pending -> "Pending"
            QuotationStatus.Sent -> "Waiting for decision"
            QuotationStatus.Approved -> "Approved"
            QuotationStatus.Rejected -> "Rejected"
            QuotationStatus.Expired -> "Expired"
            QuotationStatus.Good -> "Good Check"

        }
    }

    private fun getStatusColor(status: QuotationStatus): Int {
        return when (status) {
            QuotationStatus.Pending -> ContextCompat.getColor(requireContext(), R.color.orange)
            QuotationStatus.Sent -> ContextCompat.getColor(requireContext(), R.color.blue)
            QuotationStatus.Approved -> ContextCompat.getColor(requireContext(), R.color.green)
            QuotationStatus.Rejected -> ContextCompat.getColor(requireContext(), R.color.red)
            QuotationStatus.Expired -> ContextCompat.getColor(requireContext(), R.color.gray)
            QuotationStatus.Good -> ContextCompat.getColor(requireContext(), R.color.green)
        }
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(amount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}