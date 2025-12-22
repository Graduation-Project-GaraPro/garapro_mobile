package com.example.garapro.ui.emergencies

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.model.emergencies.Emergency
import com.example.garapro.data.model.emergencies.CreateEmergencyRequest

import com.example.garapro.data.model.emergencies.Garage
import com.example.garapro.data.model.emergencies.EmergencyStatus
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

import com.example.garapro.hubs.EmergencySignalRService
import kotlinx.coroutines.flow.collect

class EmergencyViewModel : ViewModel() {
    private val repository = EmergencyRepository.getInstance()
    private var signalRService: EmergencySignalRService? = null

    private val _emergencyState = MutableLiveData<EmergencyState>(EmergencyState.Idle)
    val emergencyState: LiveData<EmergencyState> = _emergencyState
    
    private val _nearbyGarages = MutableLiveData<List<Garage>>(emptyList())
    val nearbyGarages: LiveData<List<Garage>> = _nearbyGarages

    private val _selectedGarage = MutableLiveData<Garage?>(null)
    val selectedGarage: LiveData<Garage?> = _selectedGarage

    private val _assignedGarage = MutableLiveData<Garage?>(null)
    val assignedGarage: LiveData<Garage?> = _assignedGarage
    private var currentEmergency: Emergency? = null

    private val _routeGeoJson = MutableLiveData<String?>(null)
    val routeGeoJson: LiveData<String?> = _routeGeoJson
    private val _etaMinutes = MutableLiveData<Int?>(null)
    val etaMinutes: LiveData<Int?> = _etaMinutes
    private val _distanceMeters = MutableLiveData<Double?>(null)
    val distanceMeters: LiveData<Double?> = _distanceMeters
    
    // Locations for routing
    private val _customerLocation = MutableLiveData<Pair<Double, Double>?>(null)
    val customerLocation: LiveData<Pair<Double, Double>?> = _customerLocation

    private val _technicianLocation = MutableLiveData<Pair<Double, Double>?>(null)
    val technicianLocation: LiveData<Pair<Double, Double>?> = _technicianLocation

    private val _technicianName = MutableLiveData<String?>(null)
    val technicianName: LiveData<String?> = _technicianName

    private val _technicianPhone = MutableLiveData<String?>(null)
    val technicianPhone: LiveData<String?> = _technicianPhone

    private val _isTechnicianArrived = MutableLiveData<Boolean>(false)
    val isTechnicianArrived: LiveData<Boolean> = _isTechnicianArrived

    private var garageLocation: Pair<Double, Double>? = null
    
    private var currentUserId: String? = null
    
    private var lastCreatedId: String? = null
    private var routePollingJob: Job? = null
    private var expirationJob: Job? = null

    fun setSignalRService(service: EmergencySignalRService) {
        this.signalRService = service
        viewModelScope.launch {
            service.events.collect { (eventName, jsonStr) ->
                try {
                    val json = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                    handleSignalREvent(eventName, json)
                } catch (e: Exception) {
                    Log.e("SignalR", "Error parsing event $eventName: ${e.message}")
                }
            }
        }
    }

    fun connectAndJoin(userId: String) {
        this.currentUserId = userId
        val service = signalRService ?: return
        viewModelScope.launch {
            if (!service.isConnected()) {
                // Logic reconnect handled by service or needs to be triggered
            }
            // Join group
            try {
                service.joinCustomerGroup(userId)
                Log.d("ViewModel", "Joined customer group: $userId")
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to join group: ${e.message}")
            }
        }
    }

    fun checkExistingEmergency(restoreId: String?) {
        if (restoreId.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                val resp = repository.getEmergencyById(restoreId)
                if (resp.isSuccess) {
                    val emergency = resp.getOrNull()
                    if (emergency != null) {
                         rehydrateEmergency(emergency)
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Error recovering emergency: ${e.message}")
            }
        }
    }

    fun fetchTechnicianInfo(userId: String, emergencyId: String) {
        viewModelScope.launch {
            try {
                val listResp = repository.getEmergenciesByCustomer(userId)
                if (listResp.isSuccess) {
                    val list = listResp.getOrNull()
                    val summary = list?.firstOrNull { it.emergencyRequestId == emergencyId }
                    summary?.assignedTechnicianName?.let { _technicianName.value = it }
                    summary?.assignedTechnicianPhone?.let { _technicianPhone.value = it }
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Error fetching tech info: ${e.message}")
            }
        }
    }

    fun refreshEmergencyData(emergencyId: String, userId: String?) {
        if (emergencyId.isBlank()) return
        viewModelScope.launch {
            if (userId != null) {
                fetchTechnicianInfo(userId, emergencyId)
            }
            try {
                val resp = repository.getEmergencyById(emergencyId)
                if (resp.isSuccess) {
                    val em = resp.getOrNull()
                    if (em != null) {
                        rehydrateEmergency(em)
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Error refreshing emergency: ${e.message}")
            }
        }
    }

    fun joinBranchGroup(branchId: String) {
        val service = signalRService ?: return
        viewModelScope.launch {
            try {
                service.joinBranchGroup(branchId)
                Log.d("ViewModel", "Joined branch group: $branchId")
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to join branch group: ${e.message}")
            }
        }
    }

    fun joinEmergencyGroup(emergencyId: String) {
        val service = signalRService ?: return
        viewModelScope.launch {
            try {
                service.joinEmergencyGroup(emergencyId)
                Log.d("ViewModel", "Joined emergency group: $emergencyId")
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to join emergency group: ${e.message}")
            }
        }
    }

    fun resumeConnection(userId: String?, emergencyId: String?, branchId: String?) {
        val service = signalRService ?: return
        viewModelScope.launch {
            if (!service.isConnected()) {
                 Log.d("ViewModel", "Resuming connection...")
                 service.start {
                     userId?.let { service.joinCustomerGroup(it) }
                     emergencyId?.let { service.joinEmergencyGroup(it) }
                     branchId?.let { service.joinBranchGroup(it) }
                 }
            } else {
                 emergencyId?.let { joinEmergencyGroup(it) }
                 branchId?.let { joinBranchGroup(it) }
            }
        }
    }

    fun stopSignalR() {
        signalRService?.stop()
    }

    fun setCustomerLocation(lat: Double, lng: Double) {
        _customerLocation.value = Pair(lat, lng)
    }

    // Helper to safely update emergency state ensuring data integrity
    private suspend fun updateEmergencyState(
        id: String, 
        newStatus: EmergencyStatus, 
        updateAction: ((Emergency) -> Emergency)? = null
    ) {
        // 1. Get current or fetch fresh
        var em = currentEmergency
        if (em == null || em.id != id || em.latitude == 0.0) {
            val resp = repository.getEmergencyById(id)
            if (resp.isSuccess) {
                em = resp.getOrNull()
            }
            // Fallback if fetch fails but we have ID
            if (em == null) em = Emergency(id = id)
        }

        // 2. Apply status and updates
        em = em!!.copy(status = newStatus)
        if (updateAction != null) {
            em = updateAction(em)
        }

        // Ensure assigned garage is loaded if we have the ID but no object
        if (_assignedGarage.value == null && !em?.assignedGarageId.isNullOrBlank()) {
             try {
                 val gId = em!!.assignedGarageId!!
                 val res = repository.getGarageById(gId)
                 if (res.isSuccess) {
                     _assignedGarage.value = res.getOrNull()
                     Log.d("ViewModel", "Lazy loaded assigned garage: ${_assignedGarage.value?.name}")
                 }
             } catch (e: Exception) {
                 Log.e("ViewModel", "Failed to lazy load garage: ${e.message}")
             }
        }

        // 3. Update references
        currentEmergency = em
        
        // 4. Update UI State
        when (newStatus) {
            EmergencyStatus.PENDING -> _emergencyState.value = EmergencyState.Success(em)
            EmergencyStatus.ACCEPTED, EmergencyStatus.ASSIGNED, EmergencyStatus.IN_PROGRESS -> 
                _emergencyState.value = EmergencyState.Confirmed(em)
            EmergencyStatus.TOWING -> _emergencyState.value = EmergencyState.Towing(em)
            EmergencyStatus.COMPLETED -> _emergencyState.value = EmergencyState.Completed(em)
            EmergencyStatus.CANCELLED -> _emergencyState.value = EmergencyState.Error("Cancelled")
            else -> {}
        }
    }

    private fun handleSignalREvent(eventName: String, json: JsonObject) {
        Log.d("SignalR_Handler", "Event: $eventName, Payload: $json")
        
        // Common ID parsing logic
        val id = sequenceOf("EmergencyRequestId", "emergencyRequestId", "emergencyId", "id")
            .mapNotNull { key -> if (json.has(key)) json.get(key).asString else null }
            .firstOrNull() ?: ""
            
        Log.d("SignalR_Handler", "Parsed ID: '$id', Current ID: '${currentEmergency?.id}'")

        if (id.isBlank() && eventName != "TechnicianLocationUpdated") {
            Log.w("SignalR_Handler", "Skipping event $eventName because ID is blank")
            return
        }

        viewModelScope.launch {
            when (eventName) {
                "EmergencyRequestCreated" -> {
                    val branchId = if (json.has("branchId")) json.get("branchId").asString else null
                    markCreated(id, branchId)
                }
                "EmergencyRequestApproved" -> {
                    val branchId = if (json.has("garageId")) json.get("garageId").asString else null
                    markApproved(id, branchId)
                }
                "TechnicianAssigned" -> {
                    Log.d("SignalR_Handler", "Processing TechnicianAssigned for ID: $id")
                    expirationJob?.cancel()
                    updateEmergencyState(id, EmergencyStatus.ASSIGNED) { em ->
                        var updated = em
                        // Parse Tech Info (Case insensitive)
                        val tName = listOf("TechnicianName", "technicianName").firstNotNullOfOrNull { k -> 
                            if (json.has(k)) json.get(k).asString else null 
                        }
                        val tPhone = listOf("TechnicianPhone", "technicianPhone").firstNotNullOfOrNull { k -> 
                            if (json.has(k)) json.get(k).asString else null 
                        }
                        
                        Log.d("SignalR_Handler", "Parsed Tech Info - Name: $tName, Phone: $tPhone")
                        
                        if (tName != null) {
                            _technicianName.value = tName
                            updated = updated.copy(assignedTechnicianName = tName)
                        }
                        if (tPhone != null) {
                            _technicianPhone.value = tPhone
                            updated = updated.copy(assignedTechnicianPhone = tPhone)
                        }
                        updated
                    }
                    _routeGeoJson.value = null

                    // Fetch full details to ensure we have consistent state
                    viewModelScope.launch {
                        val res = repository.getEmergencyById(id)
                        if (res.isSuccess) {
                            val updatedEm = res.getOrNull()
                            if (updatedEm != null) {
                                currentEmergency = updatedEm
                                if (!updatedEm.assignedTechnicianName.isNullOrBlank()) {
                                    _technicianName.value = updatedEm.assignedTechnicianName
                                }
                                if (!updatedEm.assignedTechnicianPhone.isNullOrBlank()) {
                                    _technicianPhone.value = updatedEm.assignedTechnicianPhone
                                }
                                _emergencyState.value = EmergencyState.Confirmed(updatedEm)
                            }
                        }
                    }
                }
                "EmergencyRequestInProgress" -> {
                    updateEmergencyState(id, EmergencyStatus.IN_PROGRESS)
                    
                    // Fetch full emergency details to get Technician Info immediately
                    viewModelScope.launch {
                        val res = repository.getEmergencyById(id)
                        if (res.isSuccess) {
                            val updatedEm = res.getOrNull()
                            if (updatedEm != null) {
                                currentEmergency = updatedEm
                                if (!updatedEm.assignedTechnicianName.isNullOrBlank()) {
                                    _technicianName.value = updatedEm.assignedTechnicianName
                                }
                                if (!updatedEm.assignedTechnicianPhone.isNullOrBlank()) {
                                    _technicianPhone.value = updatedEm.assignedTechnicianPhone
                                }
                                _emergencyState.value = EmergencyState.Confirmed(updatedEm)
                            }
                        }
                    }
                    
                    // Route logic
                    val garage = _assignedGarage.value ?: _selectedGarage.value
                    if (garage != null) {
                        // If we don't have tech location yet, use garage location temporarily
                        if (_technicianLocation.value == null) {
                            val gLat = garage.latitude
                            val gLng = garage.longitude
                            _technicianLocation.value = Pair(gLat, gLng)
                            
                            // Calculate initial estimate distance from Garage to Customer
                            val cLat = currentEmergency?.latitude ?: _customerLocation.value?.first
                            val cLng = currentEmergency?.longitude ?: _customerLocation.value?.second
                            
                            if (cLat != null && cLng != null) {
                                fetchRouteDirect(gLat, gLng, cLat, cLng)
                            }
                        } else {
                            fetchRouteNowFor(id)
                        }
                    } else {
                         currentUserId?.let { uid -> refreshEmergencyData(id, uid) }
                    }
                }
                "EmergencyRequestTowing" -> {
                    updateEmergencyState(id, EmergencyStatus.TOWING)
                    
                    // Fetch full emergency details to ensure consistent state
                    viewModelScope.launch {
                        val res = repository.getEmergencyById(id)
                        if (res.isSuccess) {
                            val updatedEm = res.getOrNull()
                            if (updatedEm != null) {
                                currentEmergency = updatedEm
                                if (!updatedEm.assignedTechnicianName.isNullOrBlank()) {
                                    _technicianName.value = updatedEm.assignedTechnicianName
                                }
                                if (!updatedEm.assignedTechnicianPhone.isNullOrBlank()) {
                                    _technicianPhone.value = updatedEm.assignedTechnicianPhone
                                }
                                _emergencyState.value = EmergencyState.Towing(updatedEm)
                            }
                        }
                    }

                    startTowingRoute(id)
                }
                "EmergencyRequestRejected" -> {
                     val reason = if (json.has("RejectReason")) json.get("RejectReason").asString else "Yêu cầu bị từ chối"
                     _emergencyState.value = EmergencyState.Error(reason)
                }
                "EmergencyRequestArrived", "TechnicianArrived" -> {
                    updateEmergencyState(id, EmergencyStatus.IN_PROGRESS)
                    _isTechnicianArrived.value = true
                }
                "EmergencyRequestCompleted" -> {
                    updateEmergencyState(id, EmergencyStatus.COMPLETED)
                }
                "EmergencyRequestCanceled" -> {
                     Log.d("SignalR_Handler", "Received CANCELED event.")
                     // If we are already in EXPIRED state, ignore the Cancel event
                     // because expiration naturally leads to cancellation on backend,
                     // but UI should stay on "Expired" screen to allow retry.
                     if (_emergencyState.value is EmergencyState.Expired) {
                         Log.d("SignalR_Handler", "Ignoring Cancel event because state is already EXPIRED.")
                         return@launch
                     }
                     
                     Log.d("SignalR_Handler", "Resetting state due to Cancel event.")
                     expirationJob?.cancel()
                     resetState()
                }
                "EmergencyRequestExpired" -> {
                     Log.d("SignalR_Handler", "Received EXPIRED event. Setting state to Expired.")
                     // Stop internal timer if running
                     expirationJob?.cancel()
                     
                     // Update UI
                     _emergencyState.value = EmergencyState.Expired(_assignedGarage.value)
                     
                     // Optional: Clean up backend state if needed (though event means backend already expired it)
                     currentEmergency = currentEmergency?.copy(status = EmergencyStatus.CANCELLED)
                }
                "TechnicianLocationUpdated" -> {
                    handleTechnicianLocationUpdate(json)
                }
            }
        }
    }

    private fun handleTechnicianLocationUpdate(json: JsonObject) {
         if (json.has("latitude") && json.has("longitude")) {
             val lat = json.get("latitude").asDouble
             val lng = json.get("longitude").asDouble
             _technicianLocation.value = Pair(lat, lng)
             
             if (json.has("distanceKm") && !json.get("distanceKm").isJsonNull) {
                 val dist = json.get("distanceKm").asDouble * 1000
                 // Only accept if > 0, otherwise rely on route calculation
                 if (dist > 0.1) {
                     _distanceMeters.value = dist
                 }
             }
             if (json.has("etaMinutes") && !json.get("etaMinutes").isJsonNull) {
                 _etaMinutes.value = json.get("etaMinutes").asInt
             }
             
             // Route Update Logic
             val statusString = if (json.has("status")) json.get("status").asString else ""
             val isTowing = statusString.equals("Towing", ignoreCase = true) || 
                           currentEmergency?.status == EmergencyStatus.TOWING
             
             var destLat: Double?
             var destLng: Double?
             
             if (isTowing) {
                 val garage = _assignedGarage.value ?: _selectedGarage.value
                 destLat = garage?.latitude
                 destLng = garage?.longitude
             } else {
                 destLat = currentEmergency?.latitude ?: _customerLocation.value?.first
                 destLng = currentEmergency?.longitude ?: _customerLocation.value?.second
             }
             
             if (destLat != null && destLng != null && (destLat != 0.0 || destLng != 0.0)) {
                 Log.d("RouteDebug", "Updating route from Tech($lat, $lng) to Dest($destLat, $destLng)")
                 fetchRouteDirect(lat, lng, destLat, destLng)
             } else {
                 Log.w("RouteDebug", "Destination coordinates missing or zero. destLat=$destLat, destLng=$destLng")
                 // Fallback recovery logic
                 val fallbackLat = _customerLocation.value?.first ?: 0.0
                 val fallbackLng = _customerLocation.value?.second ?: 0.0
                 if (fallbackLat != 0.0) {
                      Log.d("RouteDebug", "Using fallback customer location for route.")
                      fetchRouteDirect(lat, lng, fallbackLat, fallbackLng)
                 }
             }
         }
    }

    init {
        repository.setOnEmergencyAssignedListener { emergencyId, garageId ->
            viewModelScope.launch {
                if (currentEmergency?.id == emergencyId) {
                    _emergencyState.value = EmergencyState.Confirmed(currentEmergency!!)
                    Log.d("EmergencyFlow", "Technician accepted the emergency!")
                }
            }
        }
        repository.addOnEmergencyUpdatedListener { emergency ->
            viewModelScope.launch {
                if (currentEmergency?.id == emergency.id && emergency.status == EmergencyStatus.ACCEPTED) {
                    _emergencyState.value = EmergencyState.Confirmed(emergency)
                }
            }
        }
    }

    fun createEmergencyRequest(vehicleId: String, branchId: String, issueDescription: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _emergencyState.value = EmergencyState.Loading
            val req = CreateEmergencyRequest(
                vehicleId = vehicleId,
                branchId = branchId,
                issueDescription = issueDescription,
                latitude = latitude,
                longitude = longitude
            )
            val emergencyResult = repository.createEmergencyRequest(req)
            if (emergencyResult.isSuccess) {
                currentEmergency = emergencyResult.getOrNull()
                currentEmergency?.id?.let { 
                    lastCreatedId = it
                    joinEmergencyGroup(it)
                }
                val garage = _selectedGarage.value
                if (garage != null) {
                    _assignedGarage.value = garage
                    _emergencyState.value = EmergencyState.WaitingForGarage(garage)
                } else {
                    _emergencyState.value = EmergencyState.Success(currentEmergency!!)
                }
            } else {
                _emergencyState.value = EmergencyState.Error("Tạo yêu cầu cứu hộ thất bại")
            }
        }
    }

    fun requestEmergency(userLat: Double, userLng: Double) {
        viewModelScope.launch {
            _emergencyState.value = EmergencyState.Loading
            val garagesResult = repository.findNearbyGarages(userLat, userLng)
            if (garagesResult.isSuccess) {
                _nearbyGarages.value = garagesResult.getOrDefault(emptyList())
                val temp = Emergency(id = System.currentTimeMillis().toString(), latitude = userLat, longitude = userLng)
                currentEmergency = temp
                _emergencyState.value = EmergencyState.Success(temp)
            } else {
                _emergencyState.value = EmergencyState.Error("Không tìm thấy gara gần nhất")
            }
        }
    }

    fun refreshNearbyGarages(userLat: Double, userLng: Double) {
        viewModelScope.launch {
            val garagesResult = repository.findNearbyGarages(userLat, userLng)
            if (garagesResult.isSuccess) {
                _nearbyGarages.value = garagesResult.getOrDefault(emptyList())
            }
        }
    }

    fun selectGarage(garage: Garage) {
        _selectedGarage.value = garage
    }

    fun preselectGarage(garage: Garage) {
        _selectedGarage.value = garage
        _assignedGarage.value = garage
    }

    fun clearSelectedGarage() {
        _selectedGarage.value = null
    }

    fun confirmEmergency(emergencyId: String) {
        viewModelScope.launch {
            val garage = _selectedGarage.value
            if (garage != null) {
                _emergencyState.value = EmergencyState.WaitingForGarage(garage)
                _assignedGarage.value = garage
                Log.d("EmergencyFlow", "Waiting for technician to accept...")
            }
        }
    }

    fun cancelEmergencyRequest() {
        viewModelScope.launch {
            val id = currentEmergency?.id ?: return@launch
            val result = repository.cancelEmergency(id)
            if (result.isSuccess) {
                resetState()
            } else {
                _emergencyState.value = EmergencyState.Error("Hủy yêu cầu cứu hộ thất bại")
            }
        }
    }

    fun startTowingRoute(emergencyId: String? = null) {
        val id = emergencyId ?: currentEmergency?.id ?: return
        viewModelScope.launch {
             currentEmergency = currentEmergency?.copy(status = EmergencyStatus.TOWING)
             _emergencyState.value = EmergencyState.Towing(currentEmergency!!)
             fetchRouteNowFor(id)
        }
    }

    fun stopRoutePolling() {
        routePollingJob?.cancel()
        routePollingJob = null
    }

    fun fetchRouteNow() {
        val id = currentEmergency?.id?.takeIf { it.isNotBlank() } ?: return
        
        // TOWING MODE: Tech -> Garage
        if (currentEmergency?.status == EmergencyStatus.TOWING) {
            val techLoc = _technicianLocation.value
            val garage = _assignedGarage.value ?: _selectedGarage.value
            
            if (techLoc != null && garage != null) {
                if (garage.latitude != 0.0 && garage.longitude != 0.0) {
                    Log.d("RouteDebug", "fetchRouteNow: Immediate TOWING route draw. Tech(${techLoc.first}, ${techLoc.second}) -> Garage(${garage.latitude}, ${garage.longitude})")
                    fetchRouteDirect(techLoc.first, techLoc.second, garage.latitude, garage.longitude)
                    return
                }
            }
        }

        // IN_PROGRESS MODE: Tech -> Customer
        if (currentEmergency?.status == EmergencyStatus.IN_PROGRESS) {
             val techLoc = _technicianLocation.value
             val custLat = currentEmergency?.latitude ?: _customerLocation.value?.first
             val custLng = currentEmergency?.longitude ?: _customerLocation.value?.second
             
             if (techLoc != null && custLat != null && custLng != null) {
                  Log.d("RouteDebug", "fetchRouteNow: Immediate IN_PROGRESS route draw. Tech(${techLoc.first}, ${techLoc.second}) -> Customer($custLat, $custLng)")
                  fetchRouteDirect(techLoc.first, techLoc.second, custLat, custLng)
                  return
             }
        }
        
        // Default behavior
        viewModelScope.launch { fetchRouteOnce(id) }
    }

    fun fetchRouteNowFor(emergencyId: String) {
        if (emergencyId.isBlank()) return
        
        // Try to use direct routing if state allows, otherwise fallback to fetchRouteOnce
        if (currentEmergency?.id == emergencyId && currentEmergency?.status == EmergencyStatus.IN_PROGRESS) {
             val techLoc = _technicianLocation.value
             val custLat = currentEmergency?.latitude ?: _customerLocation.value?.first
             val custLng = currentEmergency?.longitude ?: _customerLocation.value?.second
             
             if (techLoc != null && custLat != null && custLng != null) {
                  fetchRouteDirect(techLoc.first, techLoc.second, custLat, custLng)
                  return
             }
        }
        
        viewModelScope.launch { fetchRouteOnce(emergencyId) }
    }
    
    // New function to fetch route directly using coordinates
    fun fetchRouteDirect(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) {
        viewModelScope.launch {
             // _routeGeoJson.value = null // Don't clear to avoid flickering
             val res = repository.fetchRouteDirect(fromLat, fromLon, toLat, toLon)
             if (res.isSuccess) {
                 val route = res.getOrNull()
                 val geoJson = route?.geometry?.let { toFeatureCollection(it) }
                 Log.d("RouteDebug", "MapViewModel: Fetched DIRECT route success. GeoJson length: ${geoJson?.length ?: 0}")
                 _routeGeoJson.value = geoJson
                 
                 val ds = route?.durationSeconds
                 val minutes = if (ds != null) {
                     if (ds > 300) kotlin.math.round(ds / 60.0).toInt() else kotlin.math.round(ds).toInt()
                 } else null
                 
                 // Prioritize top-level fields for consistency
                 _etaMinutes.value = route?.durationMinutes ?: minutes
                 
                 if (route?.distanceKm != null && route.distanceKm > 0) {
                     _distanceMeters.value = route.distanceKm * 1000
                 } else {
                     _distanceMeters.value = route?.distanceMeters
                 }
                 
                 // Log distance for debugging
                 Log.d("RouteDebug", "Updated distance from route API: ${_distanceMeters.value} meters")
             } else {
                 Log.w("Route", "fetch DIRECT route failed: " + (res.exceptionOrNull()?.message ?: "unknown"))
             }
        }
    }

    private suspend fun fetchRouteOnce(emergencyId: String) {
         _routeGeoJson.value = null
         val res = repository.fetchRoute(emergencyId)
         if (res.isSuccess) {
             val route = res.getOrNull()
             val geoJson = route?.geometry?.let { toFeatureCollection(it) }
             Log.d("RouteDebug", "MapViewModel: Fetched route success. GeoJson length: ${geoJson?.length ?: 0}")
             _routeGeoJson.value = geoJson
             
             val ds = route?.durationSeconds
             val minutes = if (ds != null) {
                 if (ds > 300) kotlin.math.round(ds / 60.0).toInt() else kotlin.math.round(ds).toInt()
             } else null
             
             // Prioritize top-level fields for consistency
             _etaMinutes.value = route?.durationMinutes ?: minutes
             
             if (route?.distanceKm != null && route.distanceKm > 0) {
                 _distanceMeters.value = route.distanceKm * 1000
             } else {
                 _distanceMeters.value = route?.distanceMeters
             }
             Log.d("RouteDebug", "MapViewModel: Fetched route success. Distance: ${_distanceMeters.value}")
         } else {
             Log.w("Route", "fetch failed: " + (res.exceptionOrNull()?.message ?: "unknown"))
         }
    }

    private fun toFeatureCollection(geometry: JsonElement): String {
        return try {
            when {
                geometry.isJsonObject -> {
                    val obj = geometry.asJsonObject
                    val type = obj.get("type")?.asString
                    if (type == "FeatureCollection") {
                        obj.toString()
                    } else {
                        val feature = JsonObject().apply {
                            addProperty("type", "Feature")
                            add("geometry", obj)
                        }
                        JsonObject().apply {
                            addProperty("type", "FeatureCollection")
                            add("features", JsonArray().apply { add(feature) })
                        }.toString()
                    }
                }
                geometry.isJsonArray -> {
                    val coords = geometry.asJsonArray
                    val geom = JsonObject().apply {
                        addProperty("type", "LineString")
                        add("coordinates", coords)
                    }
                    val feature = JsonObject().apply {
                        addProperty("type", "Feature")
                        add("geometry", geom)
                    }
                    JsonObject().apply {
                        addProperty("type", "FeatureCollection")
                        add("features", JsonArray().apply { add(feature) })
                    }.toString()
                }
                geometry.isJsonPrimitive -> {
                    val prim = geometry.asJsonPrimitive
                    if (prim.isString) {
                        val decoded = decodePolyline(prim.asString)
                        val coords = JsonArray().apply {
                            decoded.forEach { pair ->
                                add(JsonArray().apply {
                                    add(pair[0]) // lng
                                    add(pair[1]) // lat
                                })
                            }
                        }
                        val geom = JsonObject().apply {
                            addProperty("type", "LineString")
                            add("coordinates", coords)
                        }
                        val feature = JsonObject().apply {
                            addProperty("type", "Feature")
                            add("geometry", geom)
                        }
                        JsonObject().apply {
                            addProperty("type", "FeatureCollection")
                            add("features", JsonArray().apply { add(feature) })
                        }.toString()
                    } else {
                        JsonObject().apply {
                            addProperty("type", "FeatureCollection")
                            add("features", JsonArray())
                        }.toString()
                    }
                }
                else -> JsonObject().apply {
                    addProperty("type", "FeatureCollection")
                    add("features", JsonArray())
                }.toString()
            }
        } catch (_: Exception) {
            JsonObject().apply {
                addProperty("type", "FeatureCollection")
                add("features", JsonArray())
            }.toString()
        }
    }

    private fun decodePolyline(encoded: String): MutableList<List<Double>> {
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        val path: MutableList<List<Double>> = mutableListOf()
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val latD = lat / 1E5
            val lngD = lng / 1E5
            path.add(listOf(lngD, latD))
        }
        return path
    }

    fun markApproved(emergencyId: String, branchId: String?) {
        expirationJob?.cancel()
        viewModelScope.launch {
            var garage = _selectedGarage.value ?: _nearbyGarages.value?.firstOrNull { it.id == branchId }
            
            // Fallback: Fetch from API if not found locally and we have an ID
            if (garage == null && !branchId.isNullOrBlank()) {
                try {
                    val result = repository.getGarageById(branchId)
                    if (result.isSuccess) {
                        garage = result.getOrNull()
                    }
                } catch (e: Exception) {
                    Log.e("ViewModel", "Failed to fetch garage $branchId: ${e.message}")
                }
            }

            garage?.let { _assignedGarage.value = it }
            val idCandidate = if (emergencyId.isNotBlank()) emergencyId else (currentEmergency?.id ?: "")
            currentEmergency = currentEmergency?.copy(id = idCandidate) ?: Emergency(id = idCandidate)
            _emergencyState.value = EmergencyState.Confirmed(currentEmergency!!)
        }
    }

    fun markCreated(emergencyId: String, branchId: String?) {
        viewModelScope.launch {
            val idCandidate = if (emergencyId.isNotBlank()) emergencyId else (currentEmergency?.id ?: "")
            currentEmergency = currentEmergency?.copy(id = idCandidate) ?: Emergency(id = idCandidate)

            val branchGarage = _nearbyGarages.value?.firstOrNull { it.id == branchId }
            val currentAssigned = _assignedGarage.value ?: _selectedGarage.value
            val chosenGarage = currentAssigned ?: branchGarage
            chosenGarage?.let { _assignedGarage.value = it }

            val state = _emergencyState.value
            val alreadyWaiting = state is EmergencyState.WaitingForGarage && chosenGarage != null && state.garage.id == chosenGarage.id

            if (!alreadyWaiting) {
                val g = _assignedGarage.value
                if (g != null) {
                    _emergencyState.value = EmergencyState.WaitingForGarage(g)
                    startExpirationTimer()
                }
            }
            lastCreatedId = idCandidate
        }
    }

    private fun startExpirationTimer() {
        expirationJob?.cancel()
        
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
        routePollingJob?.cancel()
        _routeGeoJson.value = null
        _etaMinutes.value = null
        _technicianName.value = null
        _technicianPhone.value = null
        _technicianLocation.value = null
        _isTechnicianArrived.value = false
    }

    fun resetStateForRetry() {
        val current = currentEmergency
        if (current == null) {
            Log.e("ViewModel", "resetStateForRetry failed: currentEmergency is null")
            return
        }

        // Xóa lựa chọn/gán trước đó để đảm bảo trạng thái UI sạch sẽ
        _selectedGarage.value = null
        _assignedGarage.value = null
        
        val retryEmergency = current.copy(
            id = "",
            status = EmergencyStatus.PENDING,
            assignedGarageId = null,
            assignedTechnicianName = null,
            assignedTechnicianPhone = null
        )
        
        currentEmergency = retryEmergency
        _emergencyState.value = EmergencyState.Success(retryEmergency)
    }



    fun rehydrateEmergency(emergency: Emergency) {
        currentEmergency = emergency
        
        // Fetch full garage details if we have an ID but no object
        val garageId = emergency.assignedGarageId
        if (!garageId.isNullOrBlank() && _assignedGarage.value == null) {
            viewModelScope.launch {
                try {
                    val garageRes = repository.getGarageById(garageId)
                    if (garageRes.isSuccess) {
                        _assignedGarage.value = garageRes.getOrNull()
                        Log.d("ViewModel", "Rehydrated assigned garage: ${_assignedGarage.value?.name}")
                    }
                } catch (e: Exception) {
                    Log.e("ViewModel", "Failed to rehydrate garage: ${e.message}")
                }
            }
        }

        when (emergency.status) {
            EmergencyStatus.PENDING -> {
                _emergencyState.value = EmergencyState.Success(emergency)
            }
            EmergencyStatus.ACCEPTED -> {
                _emergencyState.value = EmergencyState.Confirmed(emergency)
            }
            EmergencyStatus.ASSIGNED -> {
                _emergencyState.value = EmergencyState.Confirmed(emergency)
                // Trigger tech info update if available in emergency object
                if (!emergency.assignedTechnicianName.isNullOrBlank()) {
                    _technicianName.value = emergency.assignedTechnicianName
                    _technicianPhone.value = emergency.assignedTechnicianPhone
                }
            }
            EmergencyStatus.IN_PROGRESS -> {
                _emergencyState.value = EmergencyState.Confirmed(emergency)
                if (!emergency.assignedTechnicianName.isNullOrBlank()) {
                    _technicianName.value = emergency.assignedTechnicianName
                    _technicianPhone.value = emergency.assignedTechnicianPhone
                }
                fetchRouteNowFor(emergency.id)
            }
            EmergencyStatus.TOWING -> {
                _emergencyState.value = EmergencyState.Towing(emergency)
                if (!emergency.assignedTechnicianName.isNullOrBlank()) {
                    _technicianName.value = emergency.assignedTechnicianName
                    _technicianPhone.value = emergency.assignedTechnicianPhone
                }
                fetchRouteNowFor(emergency.id)
            }
            EmergencyStatus.COMPLETED -> {
                _emergencyState.value = EmergencyState.Completed(emergency)
            }
            EmergencyStatus.CANCELLED -> {
                _emergencyState.value = EmergencyState.Error("Emergency canceled")
            }
        }
    }

}
sealed class EmergencyState {
    object Idle : EmergencyState()
    object Loading : EmergencyState()
    data class WaitingForGarage(val garage: Garage) : EmergencyState()
    data class Success(val emergency: Emergency) : EmergencyState()
    data class Confirmed(val emergency: Emergency) : EmergencyState()
    data class TowingStarted(val emergency: Emergency) : EmergencyState()
    data class Towing(val emergency: Emergency) : EmergencyState()
    data class Completed(val emergency: Emergency?) : EmergencyState()
    data class Expired(val garage: Garage?) : EmergencyState()
    data class Error(val message: String) : EmergencyState()
}
