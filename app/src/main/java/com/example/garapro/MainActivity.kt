package com.example.garapro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.remote.RetrofitInstance
import com.example.garapro.data.remote.TokenExpiredListener
import com.example.garapro.ui.login.LoginActivity
import com.example.garapro.utils.Constants
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), TokenExpiredListener {

    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenManager = TokenManager(this)
        // 🔹 Khởi tạo RetrofitInstance ở đây
        RetrofitInstance.initialize(tokenManager, this)
        // Kiểm tra token khi khởi động
        lifecycleScope.launch {
            val token = tokenManager.getAccessTokenSync()
            if (token.isNullOrEmpty()) {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            } else {
                val role = tokenManager.getUserRole() // lấy role bạn lưu khi login
                setupNavigationByRole(role)
            }
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("DeviceToken", "Current token: $token")
            } else {
                Log.w("DeviceToken", "Fetching FCM token failed", task.exception)
            }
        }

        requestNotificationPermission()


    }
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }


    private fun setupNavigationByRole(role: String?) {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val navInflater = navController.navInflater

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        when (role) {
            "Technician" -> {
                navController.graph = navInflater.inflate(R.navigation.nav_technician)
                bottomNavigation.menu.clear()
                bottomNavigation.inflateMenu(R.menu.bottom_nav_technician)
            }
            else -> {
                navController.graph = navInflater.inflate(R.navigation.nav_customer)
                bottomNavigation.menu.clear()
                bottomNavigation.inflateMenu(R.menu.bottom_nav_customer)
            }
        }

        // Setup Bottom Navigation với NavController
        bottomNavigation.setupWithNavController(navController)
    }
    override fun onTokenExpired() {
        runOnUiThread {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
//            tokenManager.clearTokens()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

}
