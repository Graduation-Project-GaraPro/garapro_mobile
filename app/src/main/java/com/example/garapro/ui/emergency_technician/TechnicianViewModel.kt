package com.example.garapro.ui.emergency_technician

import EmergencyRepository
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.emergencies.Emergency
import kotlinx.coroutines.launch

class TechnicianViewModel : ViewModel() {
    private val repository = EmergencyRepository.getInstance()

    private val _pendingEmergencies = MutableLiveData<List<Emergency>>(emptyList())
    val pendingEmergencies: LiveData<List<Emergency>> = _pendingEmergencies

    private val _acceptedEmergency = MutableLiveData<Emergency?>(null)
    val acceptedEmergency: LiveData<Emergency?> = _acceptedEmergency



    init {
        // Listen for new emergencies
        repository.addOnEmergencyUpdatedListener { emergency ->
            onEmergencyUpdated(emergency)
        }
        Log.d("TechnicianVM", "ViewModel initialized, listener added")
    }
    // THÊM: Cleanup khi ViewModel bị clear
    override fun onCleared() {
        super.onCleared()

        Log.d("TechnicianVM", "ViewModel cleared, listener removed")
    }
    private fun onEmergencyUpdated(emergency: Emergency) {
        Log.d("TechnicianVM", "New emergency received: ${emergency.id}")
        viewModelScope.launch {
            refreshPendingEmergencies()
        }
    }
    fun loadPendingEmergencies() {
        viewModelScope.launch {
            val result = repository.getPendingEmergencies()
            if (result.isSuccess) {
                _pendingEmergencies.value = result.getOrDefault(emptyList())
            }
        }
    }

    fun acceptEmergency(emergencyId: String) {
        viewModelScope.launch {
            val result = repository.acceptEmergency(emergencyId, "tech_123") // Mock technician ID
            if (result.isSuccess) {
                _acceptedEmergency.value = result.getOrNull()
                refreshPendingEmergencies()
            }
        }
    }

    private suspend fun refreshPendingEmergencies() {
        val result = repository.getPendingEmergencies()
        if (result.isSuccess) {
            _pendingEmergencies.value = result.getOrDefault(emptyList())
        }
    }
}