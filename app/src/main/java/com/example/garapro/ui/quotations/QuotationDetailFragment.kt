package com.example.garapro.ui.quotations

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garapro.R
import com.example.garapro.data.model.quotations.Quotation
import com.example.garapro.data.model.quotations.QuotationDetail
import com.example.garapro.data.model.quotations.QuotationService
import com.example.garapro.data.model.quotations.QuotationStatus
import com.example.garapro.data.model.quotations.SubmitConfirmationType
import com.example.garapro.data.remote.RetrofitInstance
import com.example.garapro.data.repository.QuotationRepository
import com.example.garapro.databinding.FragmentQuotationDetailBinding
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


        binding.etCustomerNote.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {

                val note = s?.toString() ?: ""
                viewModel.updateCustomerNote(note)
                updateNoteValidationUI(note)
            }
        })

        // Khởi tạo adapter
        adapter = QuotationServiceAdapter(
            services = emptyList(),
            onCheckChanged = { id, checked -> viewModel.onServiceCheckChanged(id, checked) },
            onPartToggle = { serviceId, categoryId, partId ->
                viewModel.togglePartSelection(serviceId, categoryId, partId)
            }
        )


        binding.rvServices.adapter = adapter
        binding.rvServices.layoutManager = LinearLayoutManager(requireContext())


    }
    private fun updateNoteValidationUI(note: String) {
        val isValid = note.length >= 10

        if (note.isNotEmpty()) {
            if (isValid) {
                binding.tilCustomerNote.error = null
                binding.tilCustomerNote.helperText = "Đã nhập ${note.length}/10 ký tự"
            } else {
                binding.tilCustomerNote.error = "Cần ít nhất 10 ký tự"
                binding.tilCustomerNote.helperText = "Đã nhập ${note.length}/10 ký tự"
            }
        } else {
            binding.tilCustomerNote.error = "Bắt buộc nhập khi có dịch vụ bị bỏ chọn"
            binding.tilCustomerNote.helperText = null
        }
    }

    private fun setupObservers() {
        viewModel.quotation.observe(viewLifecycleOwner) { quotation ->
            quotation?.let {
                setupQuotationDetails(it)
                setupUIBasedOnStatus(it.status) // 🔥 THAY ĐỔI: Setup UI theo trạng thái
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

        viewModel.pendingServiceToggle.observe(viewLifecycleOwner) {
            it?.let(::showUnselectWarning)
        }

        viewModel.canSubmit.observe(viewLifecycleOwner) { canSubmit ->
            binding.btnSubmit.isEnabled = canSubmit && (viewModel.isSubmitting.value != true)
        }

        viewModel.hasUnselectedServices.observe(viewLifecycleOwner) { hasUnselected ->
            // HIỆN customer note field khi có service bị bỏ chọn
            binding.customerNoteSection.visibility = if (hasUnselected) View.VISIBLE else View.GONE

//            if (hasUnselected) {
//                binding.tvNoteRequirement.text = "* Bắt buộc nhập ghi chú khi có dịch vụ bị bỏ chọn"
//            }
        }
    }


    private fun setupUIBasedOnStatus(status: QuotationStatus) {
        val isEditable = status == QuotationStatus.Sent

        if (isEditable) {
            // 🔥 TRẠNG THÁI SENT: Cho phép chỉnh sửa
            setupEditableMode()
        } else {
            // 🔥 CÁC TRẠNG THÁI KHÁC: Chỉ xem
            setupReadOnlyMode(status)
        }

        // 🔥 THAY ĐỔI: Cập nhật trạng thái chỉnh sửa cho adapter
        adapter.updateEditable(isEditable)
    }
    private fun setupEditableMode() {
        // Cho phép click checkbox
        adapter.updateOnCheckChanged { id, checked ->
            viewModel.onServiceCheckChanged(id, checked)
        }

        // Hiện nút gửi phản hồi
        binding.btnSubmit.visibility = View.VISIBLE
        binding.tvEditNotice.visibility = View.VISIBLE
        binding.tvReadOnlyNotice.visibility = View.GONE

        // Hiện tổng tiền đã chọn
//        binding.tvSelectedTotalLabel.visibility = View.VISIBLE
        binding.tvSelectedTotal.visibility = View.VISIBLE


        binding.customerNoteSection.visibility = View.GONE

        binding.btnReject.visibility = View.VISIBLE
        binding.btnReject.setOnClickListener {
            showRejectConfirmation()
        }

        calculateTotal()
    }

    private fun showRejectConfirmation() {
        val customerNote = viewModel.customerNote.value

        if (customerNote.isNullOrBlank() || customerNote.length < 10) {
            Snackbar.make(binding.root, "Vui lòng nhập ghi chú ít nhất 10 ký tự", Snackbar.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xác nhận từ chối")
            .setMessage("Bạn có chắc chắn muốn từ chối toàn bộ báo giá này?")
            .setPositiveButton("Từ chối") { _, _ ->
                viewModel.rejectQuotation(customerNote)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }


    /**
     * 🔥 HÀM MỚI: Setup chế độ chỉ xem (các trạng thái khác)
     */
    private fun setupReadOnlyMode(status: QuotationStatus) {
        // Vô hiệu hóa checkbox - đã được xử lý trong adapter.updateEditable(false)
        adapter.updateOnCheckChanged { _, _ ->
            // Không làm gì khi click
            showReadOnlyMessage(status)
        }

        // Ẩn nút gửi phản hồi
        binding.btnSubmit.visibility = View.GONE
        binding.tvEditNotice.visibility = View.GONE
        binding.tvReadOnlyNotice.visibility = View.VISIBLE

        // Ẩn tổng tiền đã chọn
        binding.tvlabelSelectedTotal.visibility = View.GONE
        binding.tvSelectedTotal.visibility = View.GONE

        binding.btnReject.visibility = View.GONE
        // THÊM: Ẩn customer note field
        val quotation = viewModel.quotation.value
        val hasNote = !viewModel.customerNote.value.isNullOrBlank()
        binding.customerNoteSection.visibility = if (hasNote) View.VISIBLE else View.GONE

        if (hasNote) {
            // Vô hiệu hóa edit text và hiển thị note
            binding.etCustomerNote.isEnabled = false
            binding.etCustomerNote.setText(quotation?.note)
            binding.tilCustomerNote.helperText = "Ghi chú từ khách hàng"
            binding.tilCustomerNote.boxBackgroundColor = ContextCompat.getColor(requireContext(), R.color.gray_light)
        } else {
            binding.customerNoteSection.visibility = View.GONE
        }
        // Hiện thông báo trạng thái


        binding.tvReadOnlyNotice.text = getReadOnlyMessage(status)
    }


    /**
     * 🔥 HÀM MỚI: Hiển thị thông báo khi cố chỉnh sửa trong chế độ xem
     */
    private fun showReadOnlyMessage(status: QuotationStatus) {
        val message = when (status) {
            QuotationStatus.Approved -> "Báo giá đã được chấp nhận, không thể thay đổi"
            QuotationStatus.Rejected -> "Báo giá đã bị từ chối, không thể thay đổi"
            QuotationStatus.Expired -> "Báo giá đã hết hạn, không thể thay đổi"
            QuotationStatus.Pending -> "Báo giá đang chờ xử lý, chưa thể phản hồi"
            else -> "Không thể thay đổi báo giá ở trạng thái hiện tại"
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * 🔥 HÀM MỚI: Lấy thông báo cho chế độ chỉ xem
     */
    private fun getReadOnlyMessage(status: QuotationStatus): String {
        return when (status) {
            QuotationStatus.Approved -> "Báo giá đã được chấp nhận"
            QuotationStatus.Rejected -> "Báo giá đã bị từ chối"
            QuotationStatus.Expired -> "Báo giá đã hết hạn"
            QuotationStatus.Pending -> "Báo giá đang chờ xử lý"
            else -> "Chế độ xem"
        }
    }
    private fun setupQuotationDetails(quotation: QuotationDetail) {
        binding.tvVehicleInfo.text = quotation.vehicleInfo
        binding.tvCustomerName.text = quotation.customerName
        binding.tvTotalAmount.text = formatCurrency(quotation.totalAmount)
        binding.tvStatus.text = getStatusText(quotation.status)
        binding.tvStatus.setTextColor(getStatusColor(quotation.status))

        if (!quotation.note.isNullOrBlank() && quotation.status != QuotationStatus.Sent) {
            binding.etCustomerNote.setText(quotation.note)
            binding.etCustomerNote.isEnabled = false
            binding.tilCustomerNote.helperText = "Ghi chú từ khách hàng"

        }

        calculateTotal()
    }

    private fun calculateTotal() {
        val total = viewModel.quotation.value?.quotationServices?.sumOf { service ->
            if (service.isSelected) {
                service.totalPrice + service.partCategories.flatMap { it.parts }
                    .sumOf { part -> if (part.isSelected) part.price else 0.0 }
            } else {
                0.0
            }
        } ?: 0.0

        binding.tvSelectedTotal.text = formatCurrency(total)
        updateSubmitButton(viewModel.isSubmitting.value ?: false)
    }

    private fun updateSubmitButton(isSubmitting: Boolean) {
        val canSubmit = viewModel.canSubmit.value == true
        val isRejectMode = viewModel.isRejectMode.value == true

        binding.btnSubmit.isEnabled = canSubmit && !isSubmitting
        binding.btnReject.isEnabled = !isSubmitting

        binding.btnSubmit.text = when {
            isSubmitting -> "Đang gửi..."
            isRejectMode -> "Chấp nhận một phần" // Nút này để quay lại chọn service
            else -> "Chấp nhận dịch vụ đã chọn"
        }

        binding.btnSubmit.setBackgroundColor(ContextCompat.getColor(requireContext(),
            if (isRejectMode) R.color.blue else R.color.green))
    }

    private fun showUnselectWarning(event: QuotationDetailViewModel.ServiceToggleEvent) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xác nhận bỏ chọn")
            .setMessage("Bỏ chọn dịch vụ \"${event.serviceName}\"?")
            .setPositiveButton("Bỏ chọn") { _, _ ->
                viewModel.confirmServiceToggle(event.serviceId, event.currentChecked)
                calculateTotal()
            }
            .setNegativeButton("Giữ nguyên") { _, _ ->
                viewModel.cancelServiceToggle()
            }
            .create()

        // QUAN TRỌNG: Xử lý khi dialog bị dismiss
        dialog.setOnDismissListener {
            // Nếu dialog bị dismiss mà không chọn button, cancel việc toggle
            if (viewModel.pendingServiceToggle.value != null) {
                viewModel.cancelServiceToggle()
            }
        }

        dialog.show()
    }

    private fun showSubmitConfirmation() {
        val quotation = viewModel.quotation.value ?: return

        // 🔥 KIỂM TRA VALIDATION TRƯỚC KHI SUBMIT
        if (!viewModel.validateQuotationSelection()) {
            val validationMessage = viewModel.getValidationMessage()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Thiếu thông tin")
                .setMessage(validationMessage)
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val customerNote = viewModel.customerNote.value
        val (title, message) = when (viewModel.getSubmitConfirmationType()) {
            SubmitConfirmationType.APPROVED -> {
                val totalAmount = calculateSelectedTotal(quotation)
                "Xác nhận chấp nhận" to "Bạn đang chấp nhận TOÀN BỘ dịch vụ với tổng số tiền ${formatCurrency(totalAmount)}. Tiếp tục?"
            }
            SubmitConfirmationType.REJECTED -> {
                "Xác nhận từ chối" to "Bạn có chắc chắn muốn từ chối báo giá này?"
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Xác nhận") { _, _ -> viewModel.submitCustomerResponse() }
            .setNegativeButton("Hủy", null)
            .show()
    }
    private fun calculateSelectedTotal(quotation: QuotationDetail): Double {
        var total = 0.0

        quotation.quotationServices.forEach { service ->
            if (service.isSelected) {
                // Cộng giá dịch vụ
                total += service.totalPrice

                // Cộng giá phụ tùng trong các PartCategory
                service.partCategories.forEach { category ->
                    category.parts.forEach { part ->
                        if (part.isSelected) { // chỉ tính phần nào được chọn (nếu có flag)
                            total += part.price
                        }
                    }
                }
            }
        }

        return total
    }
    private fun onSubmitSuccess() {
        Snackbar.make(binding.root, "Đã gửi phản hồi thành công", Snackbar.LENGTH_LONG).show()
        findNavController().navigateUp()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        viewModel.clearError()
    }
    private fun getStatusText(status: QuotationStatus): String {
        return when (status) {
            QuotationStatus.Pending -> "Chờ xử lý"
            QuotationStatus.Sent -> "Đã gửi"
            QuotationStatus.Approved -> "Đã duyệt"
            QuotationStatus.Rejected -> "Đã từ chối"
            QuotationStatus.Expired -> "Hết hạn"
        }
    }

    private fun getStatusColor(status: QuotationStatus): Int {
        return when (status) {
            QuotationStatus.Pending -> ContextCompat.getColor(requireContext(), R.color.orange)
            QuotationStatus.Sent -> ContextCompat.getColor(requireContext(), R.color.blue)
            QuotationStatus.Approved -> ContextCompat.getColor(requireContext(), R.color.green)
            QuotationStatus.Rejected -> ContextCompat.getColor(requireContext(), R.color.red)
            QuotationStatus.Expired -> ContextCompat.getColor(requireContext(), R.color.gray)
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