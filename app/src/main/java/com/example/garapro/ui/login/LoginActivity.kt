package com.example.garapro.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.garapro.MainActivity
import com.example.garapro.data.local.TokenManager
import com.example.garapro.ui.repairRequest.BookingActivity
import com.example.garapro.data.remote.ApiService
import com.example.garapro.data.repository.AuthRepository
import com.example.garapro.databinding.ActivityLoginBinding

import com.example.garapro.ui.home.HomeActivity
import com.example.garapro.ui.signup.SignUpActivity
import com.example.garapro.utils.Resource

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Khởi tạo ViewModel
        val apiService = ApiService.create(this)
        val tokenManager = TokenManager(this)
        val repository = AuthRepository(apiService, tokenManager)
        viewModel = LoginViewModel(repository)


        setupObservers()
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.textEmailLogin.text.toString().trim()
            val password = binding.textPasswordLogin.text.toString().trim()

            // Validate input
//            val validationError = viewModel.validateInput(email, password)
//            if (validationError != null) {
//                Toast.makeText(this, validationError, Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }

            // Thực hiện đăng nhập
            viewModel.login(email, password)
        }

        binding.textViewSignUp.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupObservers() {
        viewModel.loginState.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {

                }

                is Resource.Success -> {

                    Toast.makeText(
                        this,
                        "Đăng nhập thành công!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Chuyển sang màn hình Home
                    navigateToHome()
                }

                is Resource.Error -> {

                    Toast.makeText(
                        this,
                        result.message ?: "Đăng nhập thất bại",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }



    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}