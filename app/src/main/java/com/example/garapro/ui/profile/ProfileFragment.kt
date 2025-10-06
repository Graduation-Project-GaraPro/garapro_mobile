package com.example.garapro.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.compose.material3.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.garapro.R
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.remote.ApiService
import com.example.garapro.data.repository.AuthRepository
import com.example.garapro.ui.login.LoginActivity
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private lateinit var textViewName: TextView
    private lateinit var apiService: ApiService
    private lateinit var authRepository: AuthRepository
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textViewName = view.findViewById(R.id.txtName)
        val btnLogout = view.findViewById<Button>(R.id.btnLogout)

        val tokenManager = TokenManager(requireContext())
        apiService = ApiService.ApiClient.getApiService(requireContext(), tokenManager)
        authRepository = AuthRepository(apiService, tokenManager)

        lifecycleScope.launch {
            try {
                val response = apiService.getMe()
                if (response.isSuccessful) {
                    val user = response.body()
                    textViewName.text = user?.fullName ?: "Không có tên"
                } else {
                    textViewName.text = "Lỗi: ${response.code()}"
                }
            } catch (e: Exception) {
                textViewName.text = "Lỗi: ${e.message}"
            }
        }

        // Xử lý đăng xuất
        btnLogout.setOnClickListener {
            lifecycleScope.launch {
                authRepository.logout()
                // Quay về màn hình đăng nhập
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
    }
}
