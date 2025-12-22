package com.example.garapro.ui.repairRequest

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.repairRequest.Branch
import com.example.garapro.data.model.repairRequest.Part

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
import com.example.garapro.data.model.NetworkResult
import com.example.garapro.data.model.Vehicles.VehicleSelectableDto
import com.example.garapro.data.model.repairRequest.ArrivalWindow
import com.example.garapro.data.model.repairRequest.ChildCategoriesResponse
import com.example.garapro.data.model.repairRequest.ParentServiceCategory
import com.example.garapro.data.model.repairRequest.SubmitState

class BookingViewModel(
    private val repository: BookingRepository
) : ViewModel() {

    // LiveData declarations
    private val _vehicles = MutableLiveData<List<VehicleSelectableDto>>()
    val vehicles: LiveData<List<VehicleSelectableDto>> = _vehicles

    private val _branches = MutableLiveData<List<Branch>>()
    val branches: LiveData<List<Branch>> = _branches

    private val _serviceCategories = MutableLiveData<List<ServiceCategory>>()
    val serviceCategories: LiveData<List<ServiceCategory>> = _serviceCategories

    private val _selectedVehicle = MutableLiveData<VehicleSelectableDto?>()
    val selectedVehicle: LiveData<VehicleSelectableDto?> = _selectedVehicle

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

    private val _submitState = MutableLiveData<SubmitState>(SubmitState.Idle)
    val submitState: LiveData<SubmitState> = _submitState

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
                val vehicles = repository.getVehiclesSelectable()
                _vehicles.value = vehicles
            } catch (e: Exception) {
                _vehicles.value = emptyList()
                _errorMessage.value = "Cannot load list Vehicle"
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
                _errorMessage.value = "Cannot load list branch"
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
                _errorMessage.value = "Cannot load operating time"
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
                _errorMessage.value = "Cannot load List Service category"
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
    fun selectVehicle(vehicle: VehicleSelectableDto) {
        if (!vehicle.isSelectable) return  // chặn chọn nếu không được chọn
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
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            try {
                val vehicle = _selectedVehicle.value;
                val branch = _selectedBranch.value;
                val categories = repository.getParentServiceCategories(vehicle?.vehicleId ?: "", branch?.branchId ?: "")
                _parentServiceCategories.postValue(categories)
            } catch (e: Exception) {
                _parentServiceCategories.postValue(emptyList())
                _errorMessage.postValue("Cannot load Parent Category")
            } finally {
                _isLoading.postValue(false)
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
                val vehicleId = _selectedVehicle.value?.vehicleId
                val branchId = _selectedBranch.value?.branchId
                val response = repository.getChildServiceCategories(
                    parentId = parentId,
                    pageNumber = pageNumber,
                    pageSize = pageSize,
                    childServiceCategoryId = childServiceCategoryId,
                    searchTerm = searchTerm,
                    branchId = branchId,
                    vehicleId = vehicleId

                )
                _childServiceCategories.value = response

                // QUAN TRỌNG: Lưu toàn bộ categories khi load lần đầu
                if (childServiceCategoryId == null && searchTerm == null) {
                    _allChildCategories.value = response.data
                }
            } catch (e: Exception) {
                _errorMessage.value = "Cannot load Service"
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


            _currentChildFilter.value = null
            _allChildCategories.value = emptyList()

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
            _isLoading.value = true          // nếu vẫn muốn xài loading chung
            _submitState.value = SubmitState.Loading

            try {
                val context = applicationContext ?: throw IllegalStateException("Context not set")

                val imageParts = convertImageUrisToMultipart(context)

                val request = CreateRepairRequest(
                    branchId = _selectedBranch.value?.branchId ?: "",
                    vehicleID = _selectedVehicle.value?.vehicleId ?: "",
                    description = _description.value ?: "",
                    requestDate = _requestDate.value ?: "",
                    images = imageParts,
                    services = _selectedServices.value?.map { service ->
                        ServiceRequest(
                            serviceId = service.serviceId,
                        )
                    } ?: emptyList()
                )

                val gson = GsonBuilder().setPrettyPrinting().create()
                Log.d("RepairRequest", gson.toJson(request))

                when (val result = repository.submitRepairRequest(request)) {
                    is NetworkResult.Success -> {
                        _submitState.value = SubmitState.Success
                    }
                    is NetworkResult.Error -> {
                        Log.d("message", result.message)
                        _submitState.value = SubmitState.Error(result.message)

                    }
                }
            } catch (e: Exception) {
                Log.e("RepairRequest", "submitRepairRequest error: ${e.message}", e)
                _submitState.value = SubmitState.Error("Lỗi kết nối. Vui lòng kiểm tra mạng và thử lại.")
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

    fun resetSubmitState() {
        _submitState.value = SubmitState.Idle
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
