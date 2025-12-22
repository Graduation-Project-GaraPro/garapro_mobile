

import android.os.Looper
import android.util.Log
import com.example.garapro.data.model.emergencies.Emergency
import com.example.garapro.data.model.emergencies.EmergencyStatus
import com.example.garapro.data.model.emergencies.Garage
import com.example.garapro.data.model.emergencies.RouteResponse

import com.example.garapro.data.remote.RetrofitInstance
import com.example.garapro.data.remote.EmergencyApiService
import com.example.garapro.data.model.emergencies.CreateEmergencyRequest
import com.example.garapro.data.model.emergencies.NearbyBranchDto

class EmergencyRepository {


    companion object {
        @Volatile
        private var instance: EmergencyRepository? = null

        fun getInstance(): EmergencyRepository {
            return instance ?: synchronized(this) {
                instance ?: EmergencyRepository().also { instance = it }
            }
        }
    }

    private var onEmergencyAssigned: ((String, String) -> Unit)? = null
    private var onEmergencyUpdated: ((Emergency) -> Unit)? = null

    // THAY ĐỔI: Dùng list thay vì single listener
    private val emergencyUpdateListeners = mutableListOf<(Emergency) -> Unit>()

    // THAY ĐỔI: Add listener thay vì set
    fun addOnEmergencyUpdatedListener(listener: (Emergency) -> Unit) {
        emergencyUpdateListeners.add(listener)
        Log.d("EmergencyRepo", "Listener added, total: ${emergencyUpdateListeners.size}")
    }

    // THÊM: Remove listener khi không cần
    fun removeOnEmergencyUpdatedListener(listener: (Emergency) -> Unit) {
        emergencyUpdateListeners.remove(listener)
    }

    fun setOnEmergencyAssignedListener(listener: (String, String) -> Unit) {
        this.onEmergencyAssigned = listener
    }

    fun setOnEmergencyUpdatedListener(listener: (Emergency) -> Unit) {
        this.onEmergencyUpdated = listener
    }





    private val api: EmergencyApiService by lazy { RetrofitInstance.emergencyService }

    suspend fun createEmergency(emergency: Emergency): Result<Emergency> {
        return try {
            val response = api.createEmergencyRequest(
                CreateEmergencyRequest(
                    vehicleId = emergency.userId,
                    branchId = emergency.assignedGarageId ?: "",
                    issueDescription = "",
                    latitude = emergency.latitude,
                    longitude = emergency.longitude
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val created = response.body()!!
                emergencyUpdateListeners.forEach { listener ->
                    try { listener(created) } catch (_: Exception) {}
                }
                Result.success(created)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Create emergency failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getGarageById(id: String): Result<Garage> {
        return try {
            val response = api.getBranchById(id)
            if (response.isSuccessful && response.body() != null) {
                val b = response.body()!!
                val g = Garage(
                    id = b.branchId,
                    name = b.branchName,
                    latitude = b.latitude,
                    longitude = b.longitude,
                    address = "${b.street}, ${b.commune}, ${b.province}",
                    phone = b.phoneNumber,
                    isAvailable = b.isActive,
                    price = 0.0,
                    rating = 0f
                )
                if (g.latitude == 0.0 && g.longitude == 0.0) {
                     Log.e("MapRepository", "WARNING: Fetched garage has (0,0) coordinates! ID: ${b.branchId} Name: ${b.branchName}")
                } else {
                     Log.d("MapRepository", "Fetched garage coords: ${g.latitude}, ${g.longitude} for ID: ${b.branchId}")
                }
                Result.success(g)
            } else {
                Result.failure(Exception("Get branch failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun createEmergencyRequest(request: CreateEmergencyRequest): Result<Emergency> {
        return try {
            val response = api.createEmergencyRequest(request)
            if (response.isSuccessful) {
                val body = response.body()
                val loc = response.headers()["Location"]
                val idFromLoc = loc?.substringAfterLast("/") ?: ""
                val created = when {
                    body != null && body.id.isNotBlank() -> body
                    body != null && body.id.isBlank() && idFromLoc.isNotBlank() -> Emergency(id = idFromLoc)
                    body == null && idFromLoc.isNotBlank() -> Emergency(id = idFromLoc)
                    body != null -> body
                    else -> Emergency()
                }
                Log.d(
                    "EmergencyID",
                    "createEmergencyRequest: bodyId=" + (body?.id ?: "") + " location=" + (loc ?: "") + " finalId=" + created.id
                )
                emergencyUpdateListeners.forEach { listener ->
                    try { listener(created) } catch (_: Exception) {}
                }
                Result.success(created)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Create emergency failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findNearbyGarages(userLat: Double, userLng: Double): Result<List<Garage>> {
        return try {
            Log.d("EmergencyAPI", "GET nearby-branches lat=$userLat lng=$userLng count=5")
            val response = api.getNearbyBranches(latitude = userLat, longitude = userLng, count = 5)
            if (response.isSuccessful && response.body() != null) {
                val branches: List<NearbyBranchDto> = response.body()!!
                Log.d("EmergencyAPI", "Response ${branches.size} branches: ${branches.joinToString { it.branchName }}")
                val garages = mutableListOf<Garage>()
                for (b in branches) {
                    var fee = 0.0
                    try {
                        val feeResp = api.calculateEmergencyFee(b.distanceKm)
                        if (feeResp.isSuccessful && feeResp.body() != null) {
                            fee = feeResp.body()!!.fee
                            Log.d("EmergencyAPI", "Fee for ${b.branchName} distance=${b.distanceKm}km -> ${fee}")
                        }
                    } catch (_: Exception) {}
                    garages.add(
                        Garage(
                            id = b.branchId,
                            name = b.branchName,
                            latitude = b.latitude,
                            longitude = b.longitude,
                            address = b.address,
                            phone = b.phoneNumber,
                            isAvailable = true,
                            price = fee,
                            rating = 0f,
                            distance = b.distanceKm
                        )
                    )
                }
                Result.success(garages)
            } else {
                Log.e("EmergencyAPI", "Nearby branches failed: code=${response.code()} body=${response.errorBody()?.string()}" )
                Result.failure(Exception(response.errorBody()?.string() ?: "Nearby branches fetch failed"))
            }
        } catch (e: Exception) {
            Log.e("EmergencyAPI", "Nearby branches error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        // Tính khoảng cách đơn giản (trong thực tế dùng Haversine)
        return Math.sqrt(Math.pow(lat2 - lat1, 2.0) + Math.pow(lng2 - lng1, 2.0)) * 111.0 // km
    }

    suspend fun assignGarage(emergencyId: String, garageId: String): Result<Emergency> {
        return try {
            val response = api.acceptEmergency(emergencyId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Assign garage failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun getPendingEmergencies(): Result<List<Emergency>> {
        return try {
            val response = api.getPendingEmergencies()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Pending emergencies fetch failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptEmergency(emergencyId: String, technicianId: String): Result<Emergency> {
        return try {
            val response = api.acceptEmergency(emergencyId)
            if (response.isSuccessful && response.body() != null) {
                val accepted = response.body()!!
                onEmergencyAssigned?.invoke(emergencyId, technicianId)
                onEmergencyUpdated?.invoke(accepted)
                Result.success(accepted)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Accept emergency failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelEmergency(emergencyId: String): Result<Unit> {
        return try {
            val response = api.cancelEmergency(emergencyId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Cancel emergency failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRoute(emergencyId: String): Result<RouteResponse> {
        return try {
            Log.d("EmergencyAPI", "GET route id=" + emergencyId)
            val response = api.getRoute(emergencyId)
            Log.d("EmergencyAPI", "Route resp code=" + response.code())
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                try {
                    val geom = body.geometry
                    val type = if (geom.isJsonObject) "Object" else if (geom.isJsonArray) "Array" else if (geom.isJsonPrimitive) "Primitive" else "Unknown"
                    Log.d("EmergencyAPI", "Route body: geometryType=" + type + ", dist=" + (body.distanceMeters ?: -1.0) + ", dur=" + (body.durationSeconds ?: -1.0))
                } catch (_: Exception) {}
                Result.success(body)
            } else {
                Log.w("EmergencyAPI", "Route failed: code=" + response.code() + ", body=" + (response.errorBody()?.string() ?: ""))
                Result.failure(Exception(response.errorBody()?.string() ?: "Fetch route failed"))
            }
        } catch (e: Exception) {
            Log.e("EmergencyAPI", "Route error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchRouteDirect(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Result<RouteResponse> {
        return try {
            Log.d("EmergencyAPI", "GET route-direct from($fromLat, $fromLon) to($toLat, $toLon)")
            val response = api.getRouteDirect(fromLat, fromLon, toLat, toLon)
            Log.d("EmergencyAPI", "RouteDirect resp code=" + response.code())
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Fetch route-direct failed"))
            }
        } catch (e: Exception) {
            Log.e("EmergencyAPI", "RouteDirect error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getEmergencyById(emergencyId: String): Result<Emergency> {
        return try {
            val response = api.getEmergencyById(emergencyId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Fetch emergency failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEmergenciesByCustomer(customerId: String): Result<List<com.example.garapro.data.model.emergencies.EmergencyRequestSummary>> {
        return try {
            val response = api.getEmergenciesByCustomer(customerId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Fetch customer emergencies failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun simulateTechnicianNotification(emergency: Emergency) {
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            // Giả lập technician nhận được thông báo
            Log.d("EmergencyRepo", "Technician received emergency: ${emergency.id}")
        }, 2000)
    }

}
