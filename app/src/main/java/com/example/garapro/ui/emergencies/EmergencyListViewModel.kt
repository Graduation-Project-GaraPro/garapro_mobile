package com.example.garapro.ui.emergencies

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.garapro.data.remote.RetrofitInstance
import kotlinx.coroutines.launch

class EmergencyListViewModel : ViewModel() {
    private val _emergencies = MutableLiveData<List<EmergencySummary>>(emptyList())
    val emergencies: LiveData<List<EmergencySummary>> = _emergencies

    fun loadEmergencies(userId: String? = null) {
        if (!userId.isNullOrBlank()) {
            loadByCustomer(userId)
        } else {
            // Fallback: try to load pending (for garage/admin view?) or just empty
            // Assuming this is customer app, we might need userId.
            // If userId is null, maybe we try to get it from a Repository if available.
            // For now, let's keep loadPending logic as fallback or just empty.
            loadPending() 
        }
    }

    private fun loadPending() {
        viewModelScope.launch {
            try {
                val resp = RetrofitInstance.emergencyService.getPendingEmergencies()
                if (resp.isSuccessful) {
                    val list = resp.body() ?: emptyList()
                    _emergencies.value = list.map {
                        EmergencySummary(
                            id = it.id,
                            vehicleTitle = "Emergency ${it.id.take(8)}",
                            issue = "Roadside assistance",
                            status = it.status.name,
                            time = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(it.timestamp)),
                            garageName = it.assignedGarageId ?: ""
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadByCustomer(customerId: String) {
        viewModelScope.launch {
            try {
                val resp = RetrofitInstance.emergencyService.getEmergenciesByCustomer(customerId)
                if (resp.isSuccessful) {
                    val list = resp.body() ?: emptyList()
                    _emergencies.value = list.map { dto ->
                        EmergencySummary(
                            id = dto.emergencyRequestId,
                            vehicleTitle = (dto.vehicleName ?: "") + (if (!dto.vehicleName.isNullOrBlank()) "" else ""),
                            issue = dto.issueDescription ?: "",
                            status = dto.status,
                            time = formatRequestTime(dto.requestTime),
                            garageName = dto.address ?: "",
                            technicianName = dto.assignedTechnicianName,
                            technicianPhone = dto.assignedTechnicianPhone
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    

    private fun formatRequestTime(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw.trim()
        // Nếu có phần giây phân số quá dài, cắt xuống 3 chữ số để SimpleDateFormat parse được
        // Ví dụ: 2025-12-03T19:03:46.7122245 -> 2025-12-03T19:03:46.712
        try {
            val tIdx = s.indexOf('T')
            val dotIdx = s.indexOf('.', startIndex = if (tIdx >= 0) tIdx else 0)
            if (dotIdx > 0) {
                // tìm kết thúc phần giây phân số trước ký tự 'Z' hoặc dấu +/- timezone
                val endIdx = listOf(s.indexOf('Z', dotIdx), s.indexOf('+', dotIdx), s.indexOf('-', dotIdx))
                    .filter { it > 0 }.minOrNull() ?: s.length
                val frac = s.substring(dotIdx + 1, endIdx)
                if (frac.length > 3) {
                    val trimmed = frac.substring(0, 3)
                    s = s.substring(0, dotIdx + 1) + trimmed + s.substring(endIdx)
                }
            }
        } catch (_: Exception) {}

        // Chuẩn hóa: nếu không có 'T', thay bằng khoảng trắng
        if (!s.contains('T') && s.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?"))) {
            // đã có khoảng trắng, giữ nguyên
        }

        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", // có phân số + offset
            "yyyy-MM-dd'T'HH:mm:ssXXX",     // không phân số + offset
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", // có phân số + Z
            "yyyy-MM-dd'T'HH:mm:ss'Z'",     // không phân số + Z
            "yyyy-MM-dd'T'HH:mm:ss.SSS",    // có phân số, không offset
            "yyyy-MM-dd'T'HH:mm:ss",        // không phân số, không offset
            "yyyy-MM-dd HH:mm:ss",          // khoảng trắng thay vì T
            "dd/MM/yyyy HH:mm"               // đã là dạng chuẩn
        )
        for (p in patterns) {
            try {
                val parser = java.text.SimpleDateFormat(p)
                if (p.contains("'Z'")) parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = parser.parse(s)
                if (date != null) {
                    val fmt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm")
                    fmt.timeZone = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
                    return fmt.format(date)
                }
            } catch (_: Exception) {}
        }
        try {
            val base = s.replace('T', ' ')
            val compact = if (base.length >= 16) base.substring(0, 16) else base
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
            val date = parser.parse(compact)
            if (date != null) {
                val fmt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm")
                fmt.timeZone = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
                return fmt.format(date)
            }
        } catch (_: Exception) {}
        return s
    }
}
