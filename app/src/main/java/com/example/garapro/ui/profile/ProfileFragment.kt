package com.example.garapro.ui.profile

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.garapro.R
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.model.User
import com.example.garapro.data.remote.ApiService
import com.example.garapro.data.repository.AuthRepository
import com.example.garapro.data.repository.UserRepository
import com.example.garapro.ui.login.LoginActivity
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class ProfileFragment : Fragment() {

    private lateinit var apiService: ApiService
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var imgAvatar: ImageView
    private var imageUri: Uri? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                imageUri = result.data?.data
                imageUri?.let { uri ->
                    uploadImageToServer(uri)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val txtName = view.findViewById<TextView>(R.id.txtName)
        val txtEmail = view.findViewById<TextView>(R.id.txtEmail)
        val txtPhone = view.findViewById<TextView>(R.id.txtPhone)

        val txtDob = view.findViewById<TextView>(R.id.txtDob)
        imgAvatar = view.findViewById(R.id.imgAvatar)
        val btnLogout = view.findViewById<Button>(R.id.btnLogout)
        val btnChangeAvatar = view.findViewById<ImageView>(R.id.btnEditAvatar)
        val btnEditProfile = view.findViewById<ImageView>(R.id.btnEditProfile)


        val tokenManager = TokenManager(requireContext())
        apiService = ApiService.ApiClient.getApiService(requireContext(), tokenManager)
        authRepository = AuthRepository(apiService, tokenManager)
        userRepository = UserRepository(apiService, tokenManager)

        lifecycleScope.launch {
            try {
                val response = apiService.getMe()
                if (response.isSuccessful) {
                    val user = response.body()
                    if (user != null) {
                        txtName.text = "${user.firstName ?: ""} ${user.lastName ?: ""}"
                        txtEmail.text = "Email: ${user.email ?: "Chưa có"}"
                        txtPhone.text = "SĐT: ${user.phoneNumber ?: "Chưa có"}"

                        txtDob.text = "Ngày sinh: ${user.dateOfBirth ?: "Chưa có"}"

                        Glide.with(requireContext())
                            .load(user.avatar ?: R.drawable.ic_user)
                            .placeholder(R.drawable.ic_user)
                            .circleCrop()
                            .into(imgAvatar)
                    }

                } else {
                    txtName.text = "Lỗi: ${response.code()}"
                }
            } catch (e: Exception) {
                txtName.text = "Lỗi: ${e.message}"
            }
        }

        btnChangeAvatar.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        btnEditProfile.setOnClickListener {
            val intent = Intent(requireContext(), EditProfileActivity::class.java)
            startActivity(intent)
        }


        btnLogout.setOnClickListener {
            lifecycleScope.launch {
                authRepository.logout()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }

    }
    private fun uploadImageToServer(uri: Uri) {
        val filePath = getRealPathFromURI(uri)
        if (filePath == null) {
            Toast.makeText(requireContext(), "Không thể đọc file ảnh", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(filePath)
        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("File", file.name, requestFile)

        lifecycleScope.launch {
            try {
                val uploadResponse = apiService.uploadImage(body)
                if (uploadResponse.isSuccessful) {
                    val imageUrl = uploadResponse.body()?.imageUrl
                    if (imageUrl != null) {
                        // Cập nhật avatar user
                        updateUserAvatar(imageUrl)
                    }
                } else {
                    Toast.makeText(requireContext(), "Upload thất bại", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Lỗi upload: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUserAvatar(imageUrl: String) {
        lifecycleScope.launch {
            try {
                val updatedUser = User(avatar = imageUrl);
                val response = apiService.updateProfile(updatedUser)
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Cập nhật ảnh thành công", Toast.LENGTH_SHORT).show()
                    Glide.with(requireContext())
                        .load(imageUrl)
                        .circleCrop()
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(imgAvatar)

                } else {
                    Toast.makeText(requireContext(), "Cập nhật thất bại", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Lỗi cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = requireContext().contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            if (it.moveToFirst()) {
                return it.getString(columnIndex)
            }
        }
        return null
    }
}
