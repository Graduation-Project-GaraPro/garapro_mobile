import android.os.Looper
import android.util.Log
import com.example.garapro.data.model.emergencies.Emergency
import com.example.garapro.data.model.emergencies.EmergencyStatus
import com.example.garapro.data.model.emergencies.Garage
import java.util.logging.Handler

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





    private val mockEmergencies = mutableListOf<Emergency>(
        Emergency(
            id = "1",
            userId = "user_001",
            latitude = 21.0295797,
            longitude = 105.8524247,
            timestamp = System.currentTimeMillis() - 300000, // 5 phút trước
            status = EmergencyStatus.PENDING
        ),
        Emergency(
            id = "2",
            userId = "user_002",
            latitude = 21.033333,
            longitude = 105.849998,
            timestamp = System.currentTimeMillis() - 60000, // 1 phút trước
            status = EmergencyStatus.PENDING
        ),
        Emergency(
            id = "3",
            userId = "user_003",
            latitude = 21.037400,
            longitude = 105.834200,
            timestamp = System.currentTimeMillis() - 120000, // 2 phút trước
            status = EmergencyStatus.PENDING
        )
    )

    private val mockGarages = listOf(
        Garage(
            id = "1",
            name = "Gara Ô tô Số 1",
            latitude = 21.030,
            longitude = 105.850,
            address = "123 Trần Duy Hưng, Hà Nội",
            phone = "0912345678",
            isAvailable = true,
            price = 500000.0,
            rating = 4.5f
        ),
        Garage(
            id = "2",
            name = "Gara Sửa Xe Nhanh",
            latitude = 21.025,
            longitude = 105.855,
            address = "456 Lê Văn Lương, Hà Nội",
            phone = "0987654321",
            isAvailable = true,
            price = 450000.0,
            rating = 4.2f
        ),
        Garage(
            id = "3",
            name = "Gara Chuyên Nghiệp",
            latitude = 21.035,
            longitude = 105.845,
            address = "789 Hoàng Quốc Việt, Hà Nội",
            phone = "0978123456",
            isAvailable = false, // Không khả dụng
            price = 550000.0,
            rating = 4.8f
        )
    )

    suspend fun createEmergency(emergency: Emergency): Result<Emergency> {
        return try {
            val newEmergency = emergency.copy(
                id = System.currentTimeMillis().toString(),
                timestamp = System.currentTimeMillis(),
                status = EmergencyStatus.PENDING
            )
            mockEmergencies.add(newEmergency)

            // THAY ĐỔI: Thông báo cho tất cả listeners
            Log.d("EmergencyRepo", "Notifying ${emergencyUpdateListeners.size} listeners")
            emergencyUpdateListeners.forEach { listener ->
                try {
                    listener(newEmergency)
                } catch (e: Exception) {
                    Log.e("EmergencyRepo", "Error in listener: ${e.message}")
                }
            }

            Result.success(newEmergency)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findNearbyGarages(userLat: Double, userLng: Double): Result<List<Garage>> {
        return try {
            val availableGarages = mockGarages
                .filter { it.isAvailable }
                .map { garage ->
                    val distance = calculateDistance(
                        userLat, userLng,
                        garage.latitude, garage.longitude
                    )
                    garage.copy(distance = distance)
                }
                .sortedBy { it.distance }
                .take(3) // Lấy 3 gara gần nhất

            Result.success(availableGarages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        // Tính khoảng cách đơn giản (trong thực tế dùng Haversine)
        return Math.sqrt(Math.pow(lat2 - lat1, 2.0) + Math.pow(lng2 - lng1, 2.0)) * 111.0 // km
    }

    suspend fun assignGarage(emergencyId: String, garageId: String): Result<Emergency> {
        return try {
            val emergency = mockEmergencies.find { it.id == emergencyId }
            val updatedEmergency = emergency?.copy(
                assignedGarageId = garageId,
                status = EmergencyStatus.ACCEPTED
            )
            updatedEmergency?.let {
                mockEmergencies.remove(emergency)
                mockEmergencies.add(it)
                Result.success(it)
            } ?: Result.failure(Exception("Emergency not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun getPendingEmergencies(): Result<List<Emergency>> {
        return try {
            val pending = mockEmergencies.filter { it.status == EmergencyStatus.PENDING }
            Result.success(pending)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptEmergency(emergencyId: String, technicianId: String): Result<Emergency> {
        return try {
            val emergency = mockEmergencies.find { it.id == emergencyId }
            val updatedEmergency = emergency?.copy(
                status = EmergencyStatus.ACCEPTED,
                assignedGarageId = technicianId
            )
            updatedEmergency?.let {
                mockEmergencies.remove(emergency)
                mockEmergencies.add(it)
                // Gọi callback realtime
                onEmergencyAssigned?.invoke(emergencyId, technicianId)
                onEmergencyUpdated?.invoke(it)
                Result.success(it)
            } ?: Result.failure(Exception("Emergency not found"))
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