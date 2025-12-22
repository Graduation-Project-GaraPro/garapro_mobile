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
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.remote.ApiService
import android.widget.ImageView
import android.widget.TextView
import com.example.garapro.R

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
    private var tokenExpiredReceiver: BroadcastReceiver? = null

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

        tokenExpiredReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "TOKEN_EXPIRED") {
                    // Kiểm tra fragment còn attached không
                    if (!isAdded || context == null) return

                    Toast.makeText(context, "End session", Toast.LENGTH_LONG).show()

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
            .registerReceiver(tokenExpiredReceiver!!, IntentFilter("TOKEN_EXPIRED"))

        // Logout
        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Do you want to logout?")
                .setPositiveButton("Yes") { _, _ ->
                    viewModel.logout(requireContext())
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Cấu hình các item trong menu profile
        setupProfileMenu()



        setupObservers()
        setupMenuItems()
        viewModel.loadUserInfo()
    }

    private fun setupMenuItems() {

        // CHANGE PASSWORD
        val menuChangePass = binding.root.findViewById<View>(R.id.changePass)
        val changePassIcon = menuChangePass.findViewById<ImageView>(R.id.ivIcon)
        val changePassTitle = menuChangePass.findViewById<TextView>(R.id.tvTitle)

        changePassTitle.text = "Change Password"
        changePassIcon.setImageResource(R.drawable.ic_lock)

        menuChangePass.setOnClickListener {
            val intent = Intent(requireContext(), ChangePasswordActivity::class.java)
            startActivity(intent)
        }

        // TERMS
        val menuTerms = binding.root.findViewById<View>(R.id.menuTerms)
        val termsIcon = menuTerms.findViewById<ImageView>(R.id.ivIcon)
        val termsTitle = menuTerms.findViewById<TextView>(R.id.tvTitle)

        termsTitle.text = "Terms & Conditions"
        termsIcon.setImageResource(R.drawable.ic_empty_document)

        menuTerms.setOnClickListener {
            Toast.makeText(requireContext(), "Terms clicked", Toast.LENGTH_SHORT).show()
        }
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


    private fun setupProfileMenu() {
        // Item 2: My Vehicles
        // Với ViewBinding, include `@+id/vehiclesFragment` sẽ tạo ra binding con cho `item_profile_menu`
        val vehiclesItemBinding = binding.vehiclesFragment

        // Đổi title để không còn là "My addresses"
        vehiclesItemBinding.tvTitle.text = "My Vehicles"

        // Điều hướng sang vehiclesFragment trong nav_customer khi bấm vào root view của item
        vehiclesItemBinding.root.setOnClickListener {
            findNavController().navigate(com.example.garapro.R.id.vehiclesFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tokenExpiredReceiver?.let {
            LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(it)
        }
        tokenExpiredReceiver = null
        _binding = null
    }
}
