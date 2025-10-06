package com.example.garapro.ui.signup

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.garapro.R
import com.example.garapro.data.model.SignupRequest
import com.example.garapro.data.model.otpRequest
import com.example.garapro.data.model.otpVerifyRequest
import com.example.garapro.data.remote.ApiService
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    private lateinit var layoutOtp: LinearLayout
    private lateinit var layoutRegister: ScrollView
    private lateinit var editTextOtp: EditText

    private lateinit var editTextFirstName: EditText
    private lateinit var editTextLastName: EditText
    private lateinit var editTextEmail: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var editTextConfirmPassword: EditText
    private lateinit var editTextPhone: EditText

    private lateinit var buttonVerifyOtp: Button
    private lateinit var buttonRegister: Button

    private lateinit var apiService: ApiService
    private var pendingSignupData: SignupRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        apiService = ApiService.create(this)
        initViews()
        setupListeners()

        // Bắt đầu ở form đăng ký
        layoutRegister.visibility = View.VISIBLE
        layoutOtp.visibility = View.GONE
    }

    private fun initViews() {
        layoutOtp = findViewById(R.id.layoutOtp)
        layoutRegister = findViewById(R.id.layoutRegister)
        editTextOtp = findViewById(R.id.editTextOtp)

        editTextFirstName = findViewById(R.id.editTextFirstName)
        editTextLastName = findViewById(R.id.editTextLastName)
        editTextEmail = findViewById(R.id.editTextEmail)
        editTextPassword = findViewById(R.id.editTextPassword)
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword)
        editTextPhone = findViewById(R.id.editTextPhone) // reuse from layoutRegister XML

        buttonVerifyOtp = findViewById(R.id.buttonVerifyOtp)
        buttonRegister = findViewById(R.id.buttonRegister)
    }

    private fun setupListeners() {
        buttonRegister.setOnClickListener { onRegisterClicked() }
        buttonVerifyOtp.setOnClickListener { onVerifyOtpClicked() }
    }

    // 🟦 Bước 1: Người dùng bấm "Đăng ký" → gửi OTP
    private fun onRegisterClicked() {
        val firstName = editTextFirstName.text.toString().trim()
        val lastName = editTextLastName.text.toString().trim()
        val email = editTextEmail.text.toString().trim()
        val phone = editTextPhone.text.toString().trim()
        val password = editTextPassword.text.toString().trim()
        val confirm = editTextConfirmPassword.text.toString().trim()

        if (password != confirm) {
            showToast("Passwords do not match")
            return
        }

        if (phone.isEmpty()) {
            showToast("Please enter phone number")
            return
        }

        pendingSignupData = SignupRequest(
            email = email,
            password = password,
           firstName = firstName,
            lastName = lastName,
            confirmPassword = confirm,
            phoneNumber = phone
        )

        lifecycleScope.launch {
            try {
                val response = apiService.sentOtp(otpRequest(phone))
                if (response.isSuccessful) {
                    showToast(response.body()?.message ?: "OTP sent!")
                    layoutRegister.visibility = View.GONE
                    layoutOtp.visibility = View.VISIBLE
                } else {
                    showToast("Failed to send OTP: ${response.message()}")
                }
            } catch (e: Exception) {
                showToast("Error sending OTP: ${e.localizedMessage}")
            }
        }
    }

    // 🟩 Bước 2: Người dùng nhập OTP → xác thực và gửi form đăng ký
    private fun onVerifyOtpClicked() {
        val otp = editTextOtp.text.toString().trim()
        if (otp.isEmpty()) {
            showToast("Please enter OTP")
            return
        }

        val signupData = pendingSignupData ?: return showToast("Missing signup data")

        lifecycleScope.launch {
            try {
                // Xác thực OTP
                val verifyResponse = apiService.verifyOtp(
                    otpVerifyRequest(signupData.phoneNumber, otp)
                )

                if (!verifyResponse.isSuccessful) {
                    showToast("Invalid OTP: ${verifyResponse.message()}")
                    return@launch
                }

                showToast(verifyResponse.body()?.message ?: "OTP verified!")

                // Gửi form đăng ký
                val signupResponse = apiService.signup(signupData)
                if (signupResponse.isSuccessful) {
                    showToast(signupResponse.body()?.message ?: "Registration successful!")
                    finish()
                } else {
                    showToast("Signup failed: ${signupResponse.message()}")
                }
            } catch (e: Exception) {
                showToast("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
