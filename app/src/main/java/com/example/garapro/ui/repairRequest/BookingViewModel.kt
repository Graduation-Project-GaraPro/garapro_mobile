package com.example.garapro.ui.repairRequest

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.repairRequest.Branch
import com.example.garapro.data.model.repairRequest.Part
import com.example.garapro.data.model.repairRequest.PartRequest
import com.example.garapro.data.model.repairRequest.CreateRepairRequest
import com.example.garapro.data.model.repairRequest.Service
import com.example.garapro.data.model.repairRequest.ServiceCategory
import com.example.garapro.data.model.repairRequest.ServiceRequest
import com.example.garapro.data.model.repairRequest.Vehicle
import com.example.garapro.data.repository.repairRequest.BookingRepository
import com.example.garapro.utils.MoneyUtils
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.io.InputStream
import android.util.Log
import com.example.garapro.data.model.repairRequest.ArrivalWindow
import com.example.garapro.data.model.repairRequest.ChildCategoriesResponse
import com.example.garapro.data.model.repairRequest.ParentServiceCategory

class BookingViewModel(
    private val repository: BookingRepository
) : ViewModel() {

    // LiveData declarations
    private val _vehicles = MutableLiveData<List<Vehicle>>()
    val vehicles: LiveData<List<Vehicle>> = _vehicles

    private val _branches = MutableLiveData<List<Branch>>()
    val branches: LiveData<List<Branch>> = _branches

    private val _serviceCategories = MutableLiveData<List<ServiceCategory>>()
    val serviceCategories: LiveData<List<ServiceCategory>> = _serviceCategories

    private val _selectedVehicle = MutableLiveData<Vehicle?>()
    val selectedVehicle: LiveData<Vehicle?> = _selectedVehicle

    private val _selectedBranch = MutableLiveData<Branch?>()
    val selectedBranch: LiveData<Branch?> = _selectedBranch

    private val _selectedServices = MutableLiveData<MutableList<Service>>(mutableListOf())
    val selectedServices: LiveData<MutableList<Service>> = _selectedServices

    private val _selectedParts = MutableLiveData<MutableMap<String, Part>>(mutableMapOf())
    val selectedParts: LiveData<MutableMap<String, Part>> = _selectedParts

    private val _requestDate = MutableLiveData<String>()
    val requestDate: LiveData<String> = _requestDate

    private val _description = MutableLiveData<String>()
    val description: LiveData<String> = _description

    private val _imageUris = MutableLiveData<MutableList<Uri>>(mutableListOf())
    val imageUris: LiveData<MutableList<Uri>> = _imageUris

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _submitResult = MutableLiveData<Boolean?>()
    val submitResult: LiveData<Boolean?> = _submitResult

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _tokenExpired = MutableLiveData<Boolean>()
    val tokenExpired: LiveData<Boolean> = _tokenExpired
    private val _parentServiceCategories = MutableLiveData<List<ParentServiceCategory>>()
    val parentServiceCategories: LiveData<List<ParentServiceCategory>> = _parentServiceCategories

    private val _childServiceCategories = MutableLiveData<ChildCategoriesResponse>()
    val childServiceCategories: LiveData<ChildCategoriesResponse> = _childServiceCategories

    private val _arrivalWindows = MutableLiveData<List<ArrivalWindow>>()
    val arrivalWindows: LiveData<List<ArrivalWindow>> = _arrivalWindows

    private val _selectedParentCategory = MutableLiveData<ParentServiceCategory?>()
    val selectedParentCategory: LiveData<ParentServiceCategory?> = _selectedParentCategory

    private val _allChildCategories = MutableLiveData<List<ServiceCategory>>()
    val allChildCategories: LiveData<List<ServiceCategory>> = _allChildCategories

    private val _currentChildFilter = MutableLiveData<ChildFilterState?>()
    val currentChildFilter: LiveData<ChildFilterState?> = _currentChildFilter

    data class ChildFilterState(
        val categoryId: String?,
        val categoryName: String,
        val searchTerm: String?
    )
    // New navigation state
    private val _navigationState = MutableLiveData<NavigationState>(NavigationState.VEHICLE_SELECTION)
    val navigationState: LiveData<NavigationState> = _navigationState

    // Application Context
    private var applicationContext: Context? = null

    fun setContext(context: Context) {
        applicationContext = context.applicationContext
    }

    fun setChildFilterState(filterState: ChildFilterState?) {
        _currentChildFilter.value = filterState
    }

    fun getChildFilterState(): ChildFilterState? {
        return _currentChildFilter.value
    }
    fun loadVehicles() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val vehicles = repository.getVehicles()
                _vehicles.value = vehicles
            } catch (e: Exception) {
                _vehicles.value = emptyList()
                _errorMessage.value = "Không thể tải danh sách xe"
                checkTokenExpired(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadBranches() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val branches = repository.getBranches()
                _branches.value = branches
            } catch (e: Exception) {
                _branches.value = emptyList()
                _errorMessage.value = "Không thể tải danh sách chi nhánh"
                checkTokenExpired(e)
            } finally {
                _isLoading.value = false
            }
        }
    }


    fun loadArrivalAvailability(branchId: String, date: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val slots = repository.getArrivalAvailability(branchId, date)
                _arrivalWindows.value = slots.filter { !it.isFull && it.remaining > 0 }
            } catch (e: Exception) {
                _arrivalWindows.value = emptyList()
                _errorMessage.value = "Không thể tải khung giờ"
                checkTokenExpired(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadServiceCategories() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val categories = repository.getServiceCategories()
                _serviceCategories.value = categories
            } catch (e: Exception) {
                _serviceCategories.value = emptyList()
                _errorMessage.value = "Không thể tải danh sách dịch vụ"
                checkTokenExpired(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun checkTokenExpired(exception: Exception) {
        if (exception.message?.contains("401") == true ||
            exception.message?.contains("Unauthorized") == true) {
            _tokenExpired.value = true
        }
    }

    fun resetTokenExpired() {
        _tokenExpired.value = false
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    // Selection methods
    fun selectVehicle(vehicle: Vehicle) {
        _selectedVehicle.value = vehicle
        _navigationState.value = NavigationState.BRANCH_SELECTION
    }

    fun selectBranch(branch: Branch) {
        _selectedBranch.value = branch
        _navigationState.value = NavigationState.SERVICE_SELECTION
    }

    fun toggleServiceSelection(service: Service) {
        val currentList = _selectedServices.value ?: mutableListOf()
        if (currentList.any { it.serviceId == service.serviceId }) {
            currentList.removeAll { it.serviceId == service.serviceId }
            removePartsForService(service)
        } else {
            currentList.add(service)
        }
        _selectedServices.value = currentList

        if (currentList.isNotEmpty()) {
            _navigationState.value = NavigationState.DETAILS_ENTRY
        }
    }

    private fun removePartsForService(service: Service) {
        val currentParts = _selectedParts.value ?: mutableMapOf()
        service.partCategories.flatMap { it.parts }.forEach { part ->
            currentParts.remove(part.partId)
        }
        _selectedParts.value = currentParts
    }

    fun selectPartForService(service: Service, part: Part) {
        val currentParts = _selectedParts.value ?: mutableMapOf()

        Log.d("PartSelection", "=== START ===")
        Log.d("PartSelection", "Service: ${service.serviceName}, isAdvanced: ${service.isAdvanced}")
        Log.d("PartSelection", "Part: ${part.name}, ID: ${part.partId}")
        Log.d("PartSelection", "Current selected parts: ${currentParts.keys}")

        if (currentParts.containsKey(part.partId)) {
            Log.d("PartSelection", "Part already selected - REMOVING")
            currentParts.remove(part.partId)
        } else {
            Log.d("PartSelection", "Part not selected - ADDING")
            if (service.isAdvanced) {
                Log.d("PartSelection", "Advanced service logic")
                handleAdvancedServiceSelection(service, part, currentParts)
            } else {
                Log.d("PartSelection", "Normal service logic")
                handleNormalServiceSelection(service, part, currentParts)
            }
        }

        Log.d("PartSelection", "Final selected parts: ${currentParts.keys}")
        Log.d("PartSelection", "=== END ===")

        _selectedParts.value = currentParts
    }

    private fun handleAdvancedServiceSelection(service: Service, part: Part, currentParts: MutableMap<String, Part>) {
        // Tìm partCategory của part mới
        val selectedPartCategory = service.partCategories.find { category ->
            category.parts.any { it.partId == part.partId }
        }
        Log.d("PartSelection", "Selected part category: ${selectedPartCategory?.categoryName}")

        // Tìm part đã chọn trong cùng category (nếu có)
        val existingPartInSameCategory = currentParts.values.find { existingPart ->
            service.partCategories.any { category ->
                category.partCategoryId == selectedPartCategory?.partCategoryId &&
                        category.parts.any { it.partId == existingPart.partId }
            }
        }
        Log.d("PartSelection", "Existing part in same category: ${existingPartInSameCategory?.name}")

        // Nếu đã có part trong cùng category, xóa part cũ
        existingPartInSameCategory?.let {
            Log.d("PartSelection", "Removing existing part: ${it.name}")
            currentParts.remove(it.partId)
        }

        // Thêm part mới
        currentParts[part.partId] = part
        Log.d("PartSelection", "Added new part: ${part.name}")
    }

    private fun handleNormalServiceSelection(service: Service, part: Part, currentParts: MutableMap<String, Part>) {
        // Xóa tất cả parts thuộc service này
        val partsToRemove = currentParts.values.filter { existingPart ->
            service.partCategories.any { category ->
                category.parts.any { it.partId == existingPart.partId }
            }
        }
        Log.d("PartSelection", "Parts to remove for normal service: ${partsToRemove.map { it.name }}")

        partsToRemove.forEach { partToRemove ->
            currentParts.remove(partToRemove.partId)
        }

        // Thêm part mới
        currentParts[part.partId] = part
        Log.d("PartSelection", "Added single part: ${part.name}")
    }

    fun setRequestDate(date: String) {
        _requestDate.value = date
    }

    fun setDescription(description: String) {
        _description.value = description
    }

    fun addImageUri(uri: Uri) {
        val currentUris = _imageUris.value ?: mutableListOf()
        currentUris.add(uri)
        _imageUris.value = currentUris
    }

    fun removeImageUri(uri: Uri) {
        val currentUris = _imageUris.value ?: mutableListOf()
        currentUris.remove(uri)
        _imageUris.value = currentUris
    }

    // Validation methods
    fun isServiceSelected(service: Service): Boolean {
        return _selectedServices.value?.any { it.serviceId == service.serviceId } == true
    }

    fun isPartSelected(part: Part): Boolean {
        return _selectedParts.value?.containsKey(part.partId) == true
    }

    fun calculateTotalPrice(): Double {
        val servicesPrice = _selectedServices.value?.sumOf { service ->
            MoneyUtils.calculateServicePrice(service)
        } ?: 0.0

        val partsPrice = _selectedParts.value?.values?.sumOf { it.price } ?: 0.0

        return servicesPrice + partsPrice
    }

    fun validateForm(): Boolean {
        return _selectedVehicle.value != null &&
                _selectedBranch.value != null &&
                _selectedServices.value?.isNotEmpty() == true &&
                _requestDate.value?.isNotBlank() == true &&
                _description.value?.isNotBlank() == true
    }

    // Load Parent Service Categories
    fun loadParentServiceCategories() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val categories = repository.getParentServiceCategories()
                _parentServiceCategories.value = categories
            } catch (e: Exception) {
                _parentServiceCategories.value = emptyList()
                _errorMessage.value = "Không thể tải danh mục dịch vụ"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Load Child Service Categories with filtering
    fun loadChildServiceCategories(
        parentId: String,
        pageNumber: Int = 1,
        pageSize: Int = 10,
        childServiceCategoryId: String? = null,
        searchTerm: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = repository.getChildServiceCategories(
                    parentId = parentId,
                    pageNumber = pageNumber,
                    pageSize = pageSize,
                    childServiceCategoryId = childServiceCategoryId,
                    searchTerm = searchTerm
                )
                _childServiceCategories.value = response

                // QUAN TRỌNG: Lưu toàn bộ categories khi load lần đầu
                if (childServiceCategoryId == null && searchTerm == null) {
                    _allChildCategories.value = response.data
                }
            } catch (e: Exception) {
                _errorMessage.value = "Không thể tải dịch vụ"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Select Parent Category
    fun selectParentCategory(category: ParentServiceCategory) {
        val isChanged = _selectedParentCategory.value?.serviceCategoryId != category.serviceCategoryId

        _selectedParentCategory.value = category

        if (isChanged) {
            //  ĐỔI PARENT: xoá sạch service/part đã chọn ở parent cũ
            clearSelectionsForNewParent()

            // (khuyến nghị) reset filter & data con để tránh “treo” state cũ
            _currentChildFilter.value = null
            _allChildCategories.value = emptyList()
            // _childServiceCategories sẽ được nạp lại khi vào màn con
        }

        _navigationState.value = NavigationState.CHILD_CATEGORY_SELECTION
    }
    private fun clearSelectionsForNewParent() {
        _selectedServices.value = mutableListOf()
        _selectedParts.value = mutableMapOf()
    }

    // Update navigation state
    fun navigateToStep(step: NavigationState) {
        _navigationState.value = step
    }
    fun updateServiceSelection(hasServices: Boolean) {
        if (hasServices) {
            _navigationState.value = NavigationState.DETAILS_ENTRY
        }
    }

    fun updateDetailsCompletion(isCompleted: Boolean) {
        if (isCompleted) {
            _navigationState.value = NavigationState.CONFIRMATION
        }
    }



    // API Submission
    fun submitRepairRequest() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val context = applicationContext ?: throw IllegalStateException("Context not set")

                val imageParts = convertImageUrisToMultipart(context)

                val request = CreateRepairRequest(
                    branchId = _selectedBranch.value?.branchId ?: "",
                    vehicleID = _selectedVehicle.value?.vehicleID ?: "",
                    description = _description.value ?: "",
                    requestDate = _requestDate.value ?: "",
                    images = imageParts,
                    services = _selectedServices.value?.map { service ->
                        ServiceRequest(
                            serviceId = service.serviceId,
                            parts = _selectedParts.value?.values
                                ?.filter { part ->
                                    service.partCategories.any { category ->
                                        category.parts.any { p -> p.partId == part.partId }
                                    }
                                }
                                ?.map { PartRequest(it.partId) } ?: emptyList()
                        )
                    } ?: emptyList()
                )
                val gson = GsonBuilder().setPrettyPrinting().create()
                Log.e("RepairRequest", gson.toJson(request))

// ✅ Cách 2: Log chi tiết từng phần nếu muốn tách riêng
                Log.d("RepairRequest", """
                        Branch ID: ${request.branchId}
                        Vehicle ID: ${request.vehicleID}
                        Description: ${request.description}
                        Request Date: ${request.requestDate}
                        Images: ${request.images.joinToString(", ")}
                        Services: ${
                                        request.services.joinToString("\n") { service ->
                                            " - ServiceID: ${service.serviceId}, Parts: ${
                                                service.parts.joinToString(", ") { it.partId }
                                            }"
                                        }
                                    }
                    """.trimIndent())

                val result = repository.submitRepairRequest(request)
                _submitResult.value = result as? Boolean ?: false


                if (result is Boolean && !result) {
                    _errorMessage.value = "Gửi yêu cầu thất bại. Vui lòng thử lại."
                }
            } catch (e: Exception) {
                _submitResult.value = false
                _errorMessage.value = "Lỗi kết nối. Vui lòng kiểm tra mạng và thử lại."
                checkTokenExpired(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun convertImageUrisToMultipart(context: Context): List<MultipartBody.Part> {
        return withContext(Dispatchers.IO) {
            _imageUris.value?.mapNotNull { uri ->
                convertUriToMultipart(uri, context)
            } ?: emptyList()
        }
    }

    private fun convertUriToMultipart(uri: Uri, context: Context): MultipartBody.Part? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = createTempFileFromInputStream(inputStream, "image_${System.currentTimeMillis()}")
            val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), file)
            MultipartBody.Part.createFormData("images", file.name, requestFile)
        } catch (e: Exception) {
            null
        }
    }

    private fun createTempFileFromInputStream(inputStream: InputStream?, prefix: String): File {
        return File.createTempFile(prefix, ".jpg").apply {
            inputStream?.use { input ->
                outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            deleteOnExit()
        }
    }

    fun resetSubmitResult() {
        _submitResult.value = null
    }


}

sealed class NavigationState {
    object VEHICLE_SELECTION : NavigationState()
    object BRANCH_SELECTION : NavigationState()
    object PARENT_CATEGORY_SELECTION : NavigationState()
    object CHILD_CATEGORY_SELECTION : NavigationState()
    object SERVICE_SELECTION : NavigationState()
    object DETAILS_ENTRY : NavigationState()
    object CONFIRMATION : NavigationState()
}
