package com.example.garapro.ui.profile

import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.garapro.R
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.model.User
import com.example.garapro.data.remote.ApiService
import com.example.garapro.data.repository.UserRepository
import com.example.garapro.databinding.ActivityEditProfileBinding
import com.example.garapro.utils.Resource
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import java.util.*

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var tokenManager: TokenManager
    private lateinit var apiService: ApiService
    private lateinit var repository: UserRepository
    private lateinit var viewModel: ProfileViewModel

    private var selectedDate: DateTime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🧭 Toolbar setup
        setSupportActionBar(binding.topAppBar)

        // ✅ Khởi tạo ViewModel
        tokenManager = TokenManager(this)
        apiService = ApiService.ApiClient.getApiService(this, tokenManager)
        repository = UserRepository(apiService)
        viewModel = ProfileViewModel(repository)

        setupObservers()

        binding.topAppBar.setNavigationOnClickListener {
            finish();
        }

        // 🔹 Load thông tin người dùng
        viewModel.loadUserInfo()

        // 🔹 Chọn ngày sinh
        binding.edtDob.setOnClickListener { showDatePicker() }

        // 🔹 Chọn ảnh
        binding.btnChangeAvatar.setOnClickListener {
            Toast.makeText(this, "Chọn ảnh đại diện (chưa xử lý)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_edit_profile, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveProfile()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Quan sát LiveData từ ViewModel */
    private fun setupObservers() {
        viewModel.userState.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    // TODO: Hiện loading UI
                }
                is Resource.Success -> {
                    val user = result.data!!
                    binding.edtFirstName.setText(user.firstName ?: "")
                    binding.edtLastName.setText(user.lastName ?: "")
                    binding.edtEmail.setText(user.email ?: "")
                    binding.edtPhone.setText(user.phoneNumber ?: "")
                    user.dateOfBirth?.let {
                        selectedDate = it
                        binding.edtDob.setText(it.toString("dd/MM/yyyy"))
                    }

                    if (!user.avatar.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(user.avatar)
                            .placeholder(R.drawable.ic_user)
                            .into(binding.imgAvatar)
                    }
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.updateState.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    Toast.makeText(this, "Đang lưu...", Toast.LENGTH_SHORT).show()
                }
                is Resource.Success -> {
                    Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDatePicker() {
        val now = Calendar.getInstance()
        val year = selectedDate?.year ?: now.get(Calendar.YEAR)
        val month = selectedDate?.monthOfYear?.minus(1) ?: now.get(Calendar.MONTH)
        val day = selectedDate?.dayOfMonth ?: now.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(
            this,
            { _, y, m, d ->
                selectedDate = DateTime(y, m + 1, d, 0, 0)
                binding.edtDob.setText(selectedDate!!.toString("dd/MM/yyyy"))
            },
            year, month, day
        )
        datePicker.datePicker.maxDate = System.currentTimeMillis()
        datePicker.show()
    }

    private fun saveProfile() {
        val firstName = binding.edtFirstName.text.toString().trim()
        val lastName = binding.edtLastName.text.toString().trim()
        val phone = binding.edtPhone.text.toString().trim()
        val dob = binding.edtDob.text.toString().trim()

        if (firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ họ tên", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedUser = User(
            firstName = firstName,
            lastName = lastName,
            phoneNumber = phone,
            dateOfBirth = selectedDate
        )

        viewModel.updateUser(updatedUser)
    }
}
