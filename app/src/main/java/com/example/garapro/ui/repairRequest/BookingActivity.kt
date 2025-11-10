package com.example.garapro.ui.repairRequest

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garapro.R
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.model.repairRequest.Branch
import com.example.garapro.data.model.repairRequest.Part
import com.example.garapro.data.model.repairRequest.Service
import com.example.garapro.data.model.repairRequest.Vehicle
import com.example.garapro.data.remote.RetrofitInstance
import com.example.garapro.data.remote.TokenExpiredListener
import com.example.garapro.data.repository.repairRequest.BookingRepository
import com.example.garapro.databinding.ActivityBookingBinding
import java.util.Calendar
import androidx.fragment.app.FragmentContainerView
class BookingActivity : AppCompatActivity(), TokenExpiredListener {

    private lateinit var binding: ActivityBookingBinding
    private lateinit var navController: NavController
    private lateinit var tokenManager: TokenManager

    private val repository by lazy { BookingRepository(this, tokenManager) }
    private val viewModelFactory by lazy { BookingViewModelFactory(repository) }

    val viewModel: BookingViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize TokenManager
        tokenManager = TokenManager(this)

        // Setup navigation với delay
        setupNavigationWithDelay()

        // Thiết lập context cho ViewModel
        viewModel.setContext(this)

        setupObservers()

        // Load initial data với delay
        initializeRetrofitAndLoadDataWithDelay()
    }

    private fun setupNavigationWithDelay() {
        // Tạo NavHostFragment programmatically
        val navHostFragment = NavHostFragment.create(R.navigation.nav_graph_booking)

        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, navHostFragment)
            .setPrimaryNavigationFragment(navHostFragment)
            .commitNow()

        // Sử dụng postDelayed để đảm bảo Fragment đã được attach
        binding.navHostFragment.post {
            navController = navHostFragment.navController
            setupToolbar()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            supportActionBar?.title = when (destination.id) {
                R.id.vehicleSelectionFragment -> "Select Vehicle"
                R.id.branchSelectionFragment -> "Select Branch"
                R.id.parentCategorySelectionFragment -> "Select Service Category"
                R.id.childCategorySelectionFragment -> "Select Service by Type"
                R.id.detailsFragment -> "Booking Information"
                R.id.confirmationFragment -> "Confirmation"
                else -> "Repair Booking"
            }
        }
    }

    private fun initializeRetrofitAndLoadDataWithDelay() {
        binding.navHostFragment.postDelayed({
            try {
                RetrofitInstance.initialize(tokenManager, this)
                viewModel.loadVehicles()
                viewModel.loadBranches()
                viewModel.loadServiceCategories()
            } catch (e: Exception) {
                Log.e("BookingActivity", "Lỗi khởi tạo Retrofit: ${e.message}")
                Toast.makeText(this, "Lỗi khởi tạo kết nối", Toast.LENGTH_SHORT).show()
            }
        }, 100) // Delay 100ms để đảm bảo Fragment đã ready
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(this) { errorMessage ->
            errorMessage?.let {
                showError(it)
                viewModel.clearErrorMessage()
            }
        }

        viewModel.tokenExpired.observe(this) { expired ->
            if (expired) {
                onTokenExpired()
                viewModel.resetTokenExpired()
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onTokenExpired() {
        runOnUiThread { showTokenExpiredDialog() }
    }

    private fun showTokenExpiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Phiên đăng nhập hết hạn")
            .setMessage("Vui lòng đăng nhập lại để tiếp tục sử dụng ứng dụng.")
            .setPositiveButton("Đăng nhập") { _, _ ->
                navigateToLogin()
            }
            .setCancelable(false)
            .show()
    }

    private fun navigateToLogin() {
        Toast.makeText(this, "Chuyển đến màn hình đăng nhập", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        return if (::navController.isInitialized) {
            navController.navigateUp() || super.onSupportNavigateUp()
        } else {
            super.onSupportNavigateUp()
        }
    }

    fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
    }

    fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }
}
