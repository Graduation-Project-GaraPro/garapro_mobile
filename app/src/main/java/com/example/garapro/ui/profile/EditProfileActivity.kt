package com.example.garapro.ui.profile

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.garapro.R
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.model.User
import com.example.garapro.data.remote.ApiService
import com.example.garapro.data.repository.UserRepository
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.util.*

class EditProfileActivity : AppCompatActivity() {

    private lateinit var apiService: ApiService
    private lateinit var tokenManager: TokenManager
    private lateinit var userRepository: UserRepository

    private lateinit var edtFirstName: EditText
    private lateinit var edtLastName: EditText
    private lateinit var edtPhone: EditText
    private lateinit var edtDob: EditText
    private lateinit var btnSave: Button

    private var selectedDateTime: DateTime? = null
    private val dateFormatter = DateTimeFormat.forPattern("dd/MM/yyyy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        // 🔹 Ánh xạ view
        edtFirstName = findViewById(R.id.edtFirstName)
        edtLastName = findViewById(R.id.edtLastName)
        edtPhone = findViewById(R.id.edtPhone)
        edtDob = findViewById(R.id.edtDob)
        btnSave = findViewById(R.id.btnSave)

        tokenManager = TokenManager(this)
        apiService = ApiService.ApiClient.getApiService(this, tokenManager)
        userRepository = UserRepository(apiService, tokenManager)

        // 🔹 Tải dữ liệu người dùng hiện tại
        lifecycleScope.launch {
            try {
                val response = apiService.getMe()
                if (response.isSuccessful) {
                    val user = response.body()
                    if (user != null) {
                        edtFirstName.setText(user.firstName ?: "")
                        edtLastName.setText(user.lastName ?: "")
                        edtPhone.setText(user.phoneNumber ?: "")
                        user.dateOfBirth?.let {
                            selectedDateTime = it
                            edtDob.setText(it.toString("dd/MM/yyyy"))
                        }
                    }
                } else {
                    Toast.makeText(this@EditProfileActivity, "Không tải được dữ liệu", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "Lỗi tải dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // 🔹 Chọn ngày sinh
        edtDob.setOnClickListener {
            val now = Calendar.getInstance()
            val year = selectedDateTime?.year ?: now.get(Calendar.YEAR)
            val month = selectedDateTime?.monthOfYear?.minus(1) ?: now.get(Calendar.MONTH)
            val day = selectedDateTime?.dayOfMonth ?: now.get(Calendar.DAY_OF_MONTH)

            val datePicker = DatePickerDialog(
                this,
                { _, y, m, d ->
                    selectedDateTime = DateTime(y, m + 1, d, 0, 0)
                    edtDob.setText(selectedDateTime!!.toString("dd/MM/yyyy"))
                },
                year, month, day
            )
            datePicker.datePicker.maxDate = System.currentTimeMillis() // 🔒 Không cho chọn ngày tương lai
            datePicker.show()
        }

        // 🔹 Sự kiện lưu
        btnSave.setOnClickListener {
            val firstName = edtFirstName.text.toString().trim()
            val lastName = edtLastName.text.toString().trim()
            val phone = edtPhone.text.toString().trim()

            if (firstName.isEmpty() || lastName.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ họ tên", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedUser = User(
                firstName = firstName,
                lastName = lastName,
                phoneNumber = phone,
                dateOfBirth = selectedDateTime,
                gender = null,
                email = null,
                avatar = null
            )

            lifecycleScope.launch {
                try {
                    val response = apiService.updateProfile(updatedUser)
                    if (response.isSuccessful) {
                        Toast.makeText(this@EditProfileActivity, "✅ Cập nhật thành công", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@EditProfileActivity, "❌ Lỗi: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@EditProfileActivity, "Lỗi cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
