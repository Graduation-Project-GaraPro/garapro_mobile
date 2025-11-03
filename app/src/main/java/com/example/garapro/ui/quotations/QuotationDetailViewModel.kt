package com.example.garapro.ui.quotations

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.quotations.*
import com.example.garapro.data.repository.QuotationRepository
import com.example.garapro.databinding.FragmentQuotationDetailBinding
import kotlinx.coroutines.launch

class QuotationDetailViewModel(
    private val repository: QuotationRepository
) : ViewModel() {

    private val _quotation = MutableLiveData<QuotationDetail?>()
    val quotation: LiveData<QuotationDetail?> = _quotation

    private val _isRejectMode = MutableLiveData<Boolean>()
    val isRejectMode: LiveData<Boolean> = _isRejectMode

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isSubmitting = MutableLiveData(false)
    val isSubmitting: LiveData<Boolean> = _isSubmitting

    private val _submitSuccess = MutableLiveData(false)
    val submitSuccess: LiveData<Boolean> = _submitSuccess

    // Thêm LiveData để force update adapter khi cancel
    private val _refreshAdapter = MutableLiveData<Unit>()
    val refreshAdapter: LiveData<Unit> = _refreshAdapter

    private val _pendingServiceToggle = MutableLiveData<ServiceToggleEvent?>()
    val pendingServiceToggle: LiveData<ServiceToggleEvent?> = _pendingServiceToggle

    private val _customerNote = MutableLiveData<String>()
    val customerNote: LiveData<String> = _customerNote

    private val _hasUnselectedServices = MutableLiveData<Boolean>()
    val hasUnselectedServices: LiveData<Boolean> = _hasUnselectedServices

    private var pendingServiceId: String? = null

    val canSubmit: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(_quotation) {
            updateCanSubmit()
            _hasUnselectedServices.value =
                it?.quotationServices?.any { service -> !service.isSelected } == true
        }
        addSource(_customerNote) { updateCanSubmit() }
        addSource(_hasUnselectedServices) { updateCanSubmit() }
    }
    data class ServiceToggleEvent(
        val serviceId: String,
        val serviceName: String,
        val currentChecked: Boolean
    )
    fun clearError() {
        _errorMessage.value = null
    }
    fun loadQuotation(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            repository.getQuotationDetailById(id)
                .onSuccess { quotation ->
                    _quotation.value = quotation
                    loadCustomerNoteFromQuotation(quotation)
                }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }
    fun updateCustomerNote(note: String) {
        _customerNote.value = note
    }
    private fun loadCustomerNoteFromQuotation(quotation: QuotationDetail) {
        // Nếu quotation có customer note, load lên
        // (Giả sử quotation có field customerNote, nếu không có thì dùng field khác)
        val note = quotation.note ?: ""
        _customerNote.value = note
        Log.d("quotation note load", note);
        // Cập nhật trạng thái ban đầu
        val hasUnselected = quotation.quotationServices.any { !it.isSelected }
        _hasUnselectedServices.value = hasUnselected
    }
    private fun updateCanSubmit() {
        val quotation = _quotation.value
        val note = _customerNote.value
        val hasUnselected = _hasUnselectedServices.value == true

        val hasSelectedServices = quotation?.quotationServices?.any { it.isSelected } == true
        val hasNote = !note.isNullOrBlank()
        val hasValidNote = hasNote && note.length >= 10

        // LOGIC MỚI:
        // - Approve: có service được chọn
        // - Reject: có note hợp lệ
        val canSubmitValue = hasSelectedServices || hasValidNote
        (canSubmit as MediatorLiveData).value = canSubmitValue

        // Cập nhật trạng thái reject
        _isRejectMode.value = !hasSelectedServices
    }

    fun onServiceCheckChanged(serviceId: String, isChecked: Boolean) {
        val service = _quotation.value?.quotationServices?.find { it.quotationServiceId == serviceId } ?: return

        if (!isChecked && service.isSelected) {
            pendingServiceId = serviceId
            _pendingServiceToggle.value = ServiceToggleEvent(serviceId, service.serviceName, isChecked)
        } else {
            updateServiceSelection(serviceId, isChecked)
        }
    }

    fun confirmServiceToggle(serviceId: String, isSelected: Boolean) {
        updateServiceSelection(serviceId, isSelected)
        _pendingServiceToggle.value = null
    }

    fun clearPendingState() {
        _pendingServiceToggle.value = null
        pendingServiceId = null
    }
    fun cancelServiceToggle() {
        _pendingServiceToggle.value = null
        pendingServiceId = null
        _refreshAdapter.value = Unit
    }

    private fun updateServiceSelection(serviceId: String, isSelected: Boolean) {
        val current = _quotation.value ?: return
        val updatedServices = current.quotationServices.map {
            if (it.quotationServiceId == serviceId) it.copy(isSelected = isSelected) else it
        }
        _quotation.value = current.copy(quotationServices = updatedServices)
        Log.d("quotation",updatedServices.count().toString());
        // CẬP NHẬT: Kiểm tra có service nào bị bỏ chọn không
        val hasUnselected = updatedServices.any { !it.isSelected }
        _hasUnselectedServices.value = hasUnselected
    }

    fun togglePartSelection(serviceId: String, partCategoryId: String, partId: String) {
        val currentQuotation = _quotation.value ?: return

        val updatedServices = currentQuotation.quotationServices.map { service ->
            if (service.quotationServiceId == serviceId) { // 🔥 ĐẢM BẢO DÙNG quotationServiceId
                val updatedCategories = if (service.isAdvanced) {
                    handleAdvancedSelection(service, partCategoryId, partId)
                } else {
                    handleNonAdvancedSelection(service, partCategoryId, partId)
                }
                service.copy(partCategories = updatedCategories)
            } else {
                service
            }
        }

        _quotation.value = currentQuotation.copy(quotationServices = updatedServices)
    }

    private fun handleAdvancedSelection(
        service: QuotationServiceDetail,
        targetCategoryId: String,
        targetPartId: String
    ): List<PartCategory> {
        return service.partCategories.map { category ->
            if (category.partCategoryId == targetCategoryId) {
                // ✅ Category đích: Toggle part selection (radio button trong category)
                val updatedParts = category.parts.map { part ->
                    part.copy(isSelected = part.quotationServicePartId == targetPartId)
                }
                category.copy(parts = updatedParts)
            } else {
                // ✅ Category khác: GIỮ NGUYÊN trạng thái (không thay đổi gì)
                category
            }
        }
    }

    private fun handleNonAdvancedSelection(
        service: QuotationServiceDetail,
        targetCategoryId: String,
        targetPartId: String
    ): List<PartCategory> {
        val targetPart = service.partCategories
            .flatMap { it.parts }
            .find { it.quotationServicePartId == targetPartId }

        // ✅ Nếu part đã được chọn -> KHÔNG CHO BỎ CHỌN (luôn phải có 1 part được chọn)
        val shouldDeselectAll = targetPart?.isSelected == true

        // 🔥 QUAN TRỌNG: Non-advanced - không cho bỏ chọn nếu đây là part duy nhất đang được chọn
        val totalSelectedParts = service.partCategories.flatMap { it.parts }.count { it.isSelected }
        val canDeselect = totalSelectedParts > 1 // Chỉ cho bỏ chọn nếu còn part khác đang được chọn

        return service.partCategories.map { category ->
            val updatedParts = if (category.partCategoryId == targetCategoryId) {
                // ✅ Category đích
                if (shouldDeselectAll && canDeselect) {
                    // Cho phép bỏ chọn part này (vì còn part khác đang được chọn)
                    category.parts.map { part ->
                        if (part.quotationServicePartId == targetPartId) {
                            part.copy(isSelected = false)
                        } else {
                            part
                        }
                    }
                } else if (shouldDeselectAll && !canDeselect) {
                    // 🔥 KHÔNG CHO BỎ CHỌN - vì đây là part duy nhất đang được chọn
                    category.parts.map { part ->
                        part.copy(isSelected = part.quotationServicePartId == targetPartId)
                    }
                } else {
                    // Chọn part mới
                    category.parts.map { part ->
                        part.copy(isSelected = part.quotationServicePartId == targetPartId)
                    }
                }
            } else {
                // ✅ Category khác: luôn bỏ chọn tất cả parts
                category.parts.map { it.copy(isSelected = false) }
            }
            category.copy(parts = updatedParts)
        }
    }



    fun isServiceFullySelected(service: QuotationServiceDetail): Boolean {
        if (!service.isSelected) return false

        return if (service.isAdvanced) {
            // ✅ Advanced: mỗi category phải có ít nhất 1 part được chọn
            service.partCategories.all { category ->
                category.parts.any { it.isSelected }
            }
        } else {
            // ✅ Non-advanced: chỉ cần có đúng 1 part được chọn trong toàn bộ service
            service.partCategories.flatMap { it.parts }.count { it.isSelected } == 1
        }
    }

    fun validateQuotationSelection(): Boolean {
        val quotation = _quotation.value ?: return false

        // ✅ Lọc các service được chọn
        val selectedServices = quotation.quotationServices.filter { it.isSelected }

        if (selectedServices.isEmpty()) {
            _errorMessage.value = "Vui lòng chọn ít nhất một service."
            return false
        }



        // ✅ Kiểm tra service nào chưa đủ parts theo logic advanced/non-advanced
        val incompleteServices = selectedServices.filterNot { isServiceFullySelected(it) }

        if (incompleteServices.isNotEmpty()) {
            _errorMessage.value = getValidationMessage();
            return false
        }

        return true
    }

    fun getValidationMessage(): String {
        val quotation = _quotation.value ?: return ""
        val selectedServices = quotation.quotationServices.filter { it.isSelected }

        if (selectedServices.isEmpty()) {
            return "Vui lòng chọn ít nhất một service."
        }

        val incompleteServices = selectedServices.filterNot { isServiceFullySelected(it) }
        return if (incompleteServices.isNotEmpty()) {
            "Các dịch vụ sau cần chọn part:\n" +
                    incompleteServices.joinToString("\n") { it.serviceName }
        } else {
            ""
        }
    }

    fun toggleServiceSelection(serviceId: String, currentCheckedState: Boolean) {
        val currentQuotation = _quotation.value ?: return

        val serviceToToggle = currentQuotation.quotationServices.find { it.quotationServiceId == serviceId }
        if (serviceToToggle == null) return

        // KHÔNG cho bỏ service required
        if (!currentCheckedState && serviceToToggle.isRequired) { // 🔥 SỬA: currentCheckedState
            _errorMessage.value = "Không thể bỏ chọn dịch vụ bắt buộc: ${serviceToToggle.serviceName}"
            return
        }

        if (!currentCheckedState && serviceToToggle.isSelected) { // 🔥 SỬA: currentCheckedState
            _pendingServiceToggle.value = ServiceToggleEvent(serviceId, serviceToToggle.serviceName, currentCheckedState) // 🔥 SỬA: currentCheckedState
        } else {
            updateServiceSelection(serviceId, currentCheckedState) // 🔥 SỬA: currentCheckedState
        }
    }

    fun rejectQuotation(customerNote: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null

            val quotation = _quotation.value ?: return@launch

            val request = CustomerResponseRequest(
                quotationId = quotation.quotationId,
                status = QuotationStatus.Rejected,
                customerNote = customerNote,
                selectedServices = emptyList(), // Từ chối tất cả
                selectedServiceParts = emptyList()
            )

            repository.submitCustomerResponse(request)
                .onSuccess { _submitSuccess.value = true }
                .onFailure { _errorMessage.value = it.message }

            _isSubmitting.value = false
        }
    }





    fun getSubmitConfirmationType(): SubmitConfirmationType {
        val quotation = _quotation.value ?: return SubmitConfirmationType.REJECTED

        // LOGIC MỚI: Nếu có BẤT KỲ service nào bị bỏ chọn => TỪ CHỐI
        val hasUnselectedServices = quotation.quotationServices.any { !it.isSelected && it.isRequired }

        return if (hasUnselectedServices) {
            SubmitConfirmationType.REJECTED
        } else {
            SubmitConfirmationType.APPROVED
        }
    }

    fun submitCustomerResponse() {
        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null

            val quotation = _quotation.value ?: return@launch
            val selectedServices = quotation.quotationServices
                .filter { it.isSelected }
                .map { SelectedService(it.quotationServiceId) }

            val selectedParts = quotation.quotationServices
                .flatMap { it.partCategories }
                .flatMap { it.parts }
                .filter { it.isSelected }
                .map { SelectedServicePart(it.quotationServicePartId) }
            Log.d("quotation Serivce ",selectedServices.toString());
            Log.d("quotation part",selectedParts.toString());

            val status = if (quotation.quotationServices.any{ !it.isSelected}) QuotationStatus.Rejected else QuotationStatus.Approved
            Log.d("quotation note", _customerNote.value.toString());



            Log.d("quotation Request", CustomerResponseRequest(
                                quotationId = quotation.quotationId,
                                status = status,
                                customerNote = _customerNote.value,
                                selectedServices = selectedServices,
                                selectedServiceParts = selectedParts
                            ).toString());



           repository.submitCustomerResponse(
                CustomerResponseRequest(
                    quotationId = quotation.quotationId,
                    status = status,
                    customerNote = _customerNote.value,
                    selectedServices = selectedServices,
                    selectedServiceParts = selectedParts
                )
            ).onSuccess { _submitSuccess.value = true }
                .onFailure { _errorMessage.value = it.message }

            _isSubmitting.value = false
        }
    }

}