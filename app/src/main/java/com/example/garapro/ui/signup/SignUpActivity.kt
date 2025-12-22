package com.example.garapro.ui.signup

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.garapro.R
import com.example.garapro.data.model.SecurityPolicy
import com.example.garapro.data.model.SignupRequest
import com.example.garapro.data.model.otpRequest
import com.example.garapro.data.model.otpVerifyRequest
import com.example.garapro.data.remote.ApiService
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    private lateinit var layoutOtp: LinearLayout
    private lateinit var layoutRegister: ScrollView
    private lateinit var editTextOtp: EditText

    private lateinit var layoutPassword: TextInputLayout
    private lateinit var editTextFirstName: EditText
    private lateinit var editTextLastName: EditText
    private lateinit var editTextEmail: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var editTextConfirmPassword: EditText
    private lateinit var editTextPhone: EditText

    private lateinit var buttonVerifyOtp: Button
    private lateinit var buttonRegister: Button


    private lateinit var layoutEmail: TextInputLayout
    private lateinit var layoutPhone: TextInputLayout

    private var securityPolicy: SecurityPolicy? = null

    private lateinit var apiService: ApiService
    private var pendingSignupData: SignupRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        apiService = ApiService.create(this)
        initViews()
        setupListeners()
        loadSecurityPolicy()
        // Bắt đầu ở form đăng ký
        layoutRegister.visibility = View.VISIBLE
        layoutOtp.visibility = View.GONE
    }

    private fun initViews() {
        layoutOtp = findViewById(R.id.layoutOtp)
        layoutRegister = findViewById(R.id.layoutRegister)
        editTextOtp = findViewById(R.id.editTextOtp)
        layoutPassword = findViewById(R.id.layoutPassword)
        editTextFirstName = findViewById(R.id.editTextFirstName)
        editTextLastName = findViewById(R.id.editTextLastName)
        editTextEmail = findViewById(R.id.editTextEmail)
        editTextPassword = findViewById(R.id.editTextPassword)
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword)
        editTextPhone = findViewById(R.id.editTextPhone) // reuse from layoutRegister XML

        buttonVerifyOtp = findViewById(R.id.buttonVerifyOtp)
        buttonRegister = findViewById(R.id.buttonRegister)

        layoutEmail = findViewById(R.id.layoutEmail)
        layoutPhone = findViewById(R.id.layoutPhone)

    }

    private fun setupListeners() {
        buttonRegister.setOnClickListener { onRegisterClicked() }
        buttonVerifyOtp.setOnClickListener { onVerifyOtpClicked() }
    }

    private fun isValidPhone(phone: String): Boolean {
        val regex = Regex("^0\\d{9,10}$")
        return regex.matches(phone)
    }
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    // 🟦 Bước 1: Người dùng bấm "Đăng ký" → gửi OTP
    private fun onRegisterClicked() {

        layoutEmail.error = null
        layoutPhone.error = null
        layoutPassword.error = null

        val firstName = editTextFirstName.text.toString().trim()
        val lastName = editTextLastName.text.toString().trim()
        val email = editTextEmail.text.toString().trim()
        val phone = editTextPhone.text.toString().trim()
        val password = editTextPassword.text.toString().trim()
        val confirm = editTextConfirmPassword.text.toString().trim()



        var isValid = true

// EMAIL
        if (email.isEmpty()) {
            layoutEmail.error = "Email is required"
            isValid = false
        } else if (!isValidEmail(email)) {
            layoutEmail.error = "Invalid email format"
            isValid = false
        }

// PHONE
        if (phone.isEmpty()) {
            layoutPhone.error = "Phone number is required"
            isValid = false
        } else if (!isValidPhone(phone)) {
            layoutPhone.error = "Invalid phone number"
            isValid = false
        }

        if (!isValid) return


        loadSecurityPolicy()
        val policy = securityPolicy

        if (policy == null) {
            showToast("Security policy not loaded yet")
            return
        }

        val passwordErrors = validatePasswordByPolicy(password, policy)
        if (passwordErrors.isNotEmpty()) {
            layoutPassword.error = passwordErrors.joinToString("\n")
            return
        }

        if (password != confirm) {
            showToast("Passwords do not match")
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
                val response = apiService.sentOtp(otpRequest(phone, email))
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

    private fun loadSecurityPolicy() {
        lifecycleScope.launch {
            try {
                val response = apiService.getSecurityPolicy()
                if (response.isSuccessful) {
                    securityPolicy = response.body()
                } else {
                    showToast("Failed to load security policy")
                }
            } catch (e: Exception) {
                showToast("Error loading security policy")
            }
        }
    }

    private fun validatePasswordByPolicy(
        password: String,
        policy: SecurityPolicy
    ): List<String> {

        val errors = mutableListOf<String>()

        if (password.length < policy.minPasswordLength) {
            errors.add("Password must be at least ${policy.minPasswordLength} characters")
        }

        if (policy.requireUppercase && !password.any { it.isUpperCase() }) {
            errors.add("Password must contain at least one uppercase letter")
        }

        if (policy.requireNumber && !password.any { it.isDigit() }) {
            errors.add("Password must contain at least one number")
        }

        if (policy.requireSpecialChar &&
            !password.any { !it.isLetterOrDigit() }
        ) {
            errors.add("Password must contain at least one special character")
        }

        return errors
    }
    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
