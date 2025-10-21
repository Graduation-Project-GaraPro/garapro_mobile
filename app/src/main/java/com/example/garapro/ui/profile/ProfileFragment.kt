package com.example.garapro.ui.profile

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bumptech.glide.Glide
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.remote.ApiService
import com.example.garapro.data.repository.AuthRepository
import com.example.garapro.data.repository.UserRepository
import com.example.garapro.databinding.FragmentProfileBinding
import com.example.garapro.ui.login.LoginActivity
import com.example.garapro.utils.Resource
import kotlinx.coroutines.runBlocking

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var editProfileLauncher: ActivityResultLauncher<Intent>
    private lateinit var viewModel: ProfileViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tokenManager = TokenManager(requireContext())
        val apiService = ApiService.create(requireContext(), tokenManager)
        val repository = UserRepository(apiService)

        viewModel = ProfileViewModel(repository)
        editProfileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.loadUserInfo() // reload lại user mới
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "TOKEN_EXPIRED") {
                    // Kiểm tra fragment còn attached không
                    if (!isAdded || context == null) return

                    Toast.makeText(context, "Phiên đăng nhập đã hết hạn", Toast.LENGTH_LONG).show()

                    // Dùng context truyền vào thay vì requireContext()
                    val tokenManager = TokenManager(context)
                    runBlocking { tokenManager.clearTokens() }

                    // Chuyển về màn hình Login
                    val intentLogin = Intent(context, LoginActivity::class.java)
                    intentLogin.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intentLogin)
                }
            }
        }


        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(receiver, IntentFilter("TOKEN_EXPIRED"))

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc muốn đăng xuất không?")
                .setPositiveButton("Có") { _, _ ->
                    viewModel.logout(requireContext())
                }
                .setNegativeButton("Hủy", null)
                .show()
        }


        setupObservers()
        viewModel.loadUserInfo()
    }

    private fun setupObservers() {
        viewModel.userState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Resource.Loading -> {
                    // TODO: Show loading animation if needed
                }

                is Resource.Success -> {
                    val user = result.data
                    val fullName = listOfNotNull(user?.firstName, user?.lastName)
                        .joinToString(" ")
                        .trim()

                    binding.tvName.text = fullName.ifEmpty { "No name" }
                    binding.tvEmail.text = user?.email ?: "No email"

                    // Nếu có avatar
                    if (!user?.avatar.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(user.avatar)
                            .into(binding.profileImage)
                    }
                }

                is Resource.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.btnEditProfile.setOnClickListener {
            val intent = Intent(requireContext(), EditProfileActivity::class.java)
            editProfileLauncher.launch(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
