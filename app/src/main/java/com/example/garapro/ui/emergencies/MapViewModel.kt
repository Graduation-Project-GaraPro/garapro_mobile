import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.emergencies.Emergency
import com.example.garapro.data.model.emergencies.EmergencyStatus
import com.example.garapro.data.model.emergencies.Garage
import kotlinx.coroutines.delay

import kotlinx.coroutines.launch

class EmergencyViewModel : ViewModel() {
    private val repository = EmergencyRepository.getInstance()

    private val _emergencyState = MutableLiveData<EmergencyState>(EmergencyState.Idle)
    val emergencyState: LiveData<EmergencyState> = _emergencyState

    private val _nearbyGarages = MutableLiveData<List<Garage>>(emptyList())
    val nearbyGarages: LiveData<List<Garage>> = _nearbyGarages

    private val _selectedGarage = MutableLiveData<Garage?>(null)
    val selectedGarage: LiveData<Garage?> = _selectedGarage

    private val _assignedGarage = MutableLiveData<Garage?>(null)
    val assignedGarage: LiveData<Garage?> = _assignedGarage
    private var currentEmergency: Emergency? = null


    init {
        // Setup realtime listener
        repository.setOnEmergencyAssignedListener { emergencyId, garageId ->
            viewModelScope.launch {
                // Khi technician accept đơn, cập nhật state
                if (currentEmergency?.id == emergencyId) {
                    _emergencyState.value = EmergencyState.Confirmed(currentEmergency!!)
                    Log.d("EmergencyFlow", "Technician accepted the emergency!")
                }
            }
        }
    }


    fun requestEmergency(userLat: Double, userLng: Double) {
        viewModelScope.launch {
            _emergencyState.value = EmergencyState.Loading

            // Bước 1: Tạo yêu cầu cứu hộ
            val emergency = Emergency(
                userId = "user_123", // Mock user ID
                latitude = userLat,
                longitude = userLng
            )

            val emergencyResult = repository.createEmergency(emergency)

            if (emergencyResult.isSuccess) {
                currentEmergency = emergencyResult.getOrNull()
                // Bước 2: Tìm gara gần nhất
                val garagesResult = repository.findNearbyGarages(userLat, userLng)

                if (garagesResult.isSuccess) {
                    _nearbyGarages.value = garagesResult.getOrDefault(emptyList())
                    _emergencyState.value = EmergencyState.Success(emergencyResult.getOrNull()!!)
                } else {
                    _emergencyState.value = EmergencyState.Error("Không tìm thấy gara gần nhất")
                }
            } else {
                _emergencyState.value = EmergencyState.Error("Tạo yêu cầu cứu hộ thất bại")
            }
        }
    }

    fun selectGarage(garage: Garage) {
        _selectedGarage.value = garage
    }

    fun confirmEmergency(emergencyId: String) {
        viewModelScope.launch {
            val garage = _selectedGarage.value
            if (garage != null) {
                _emergencyState.value = EmergencyState.WaitingForGarage(garage)
                _assignedGarage.value = garage

                Log.d("EmergencyFlow", "Waiting for technician to accept...")
                // KHÔNG delay nữa, chờ technician accept thật
                // Technician accept sẽ trigger callback trên
            }
        }
    }


    fun getCurrentEmergency(): Emergency? {
        return currentEmergency
    }

    fun resetState() {
        _emergencyState.value = EmergencyState.Idle
        _nearbyGarages.value = emptyList()
        _selectedGarage.value = null
        _assignedGarage.value = null
        currentEmergency = null
    }
}

sealed class EmergencyState {
    object Idle : EmergencyState()
    object Loading : EmergencyState()
    data class WaitingForGarage(val garage: Garage) : EmergencyState()
    data class Success(val emergency: Emergency) : EmergencyState()
    data class Confirmed(val emergency: Emergency) : EmergencyState()
    data class Error(val message: String) : EmergencyState()
}