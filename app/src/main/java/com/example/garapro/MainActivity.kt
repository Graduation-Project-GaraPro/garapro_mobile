package com.example.garapro

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.garapro.data.local.TokenManager
import com.example.garapro.ui.login.LoginActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenManager = TokenManager(this)

        // Kiểm tra token khi khởi động
        lifecycleScope.launch {
            val token = tokenManager.getAccessTokenSync()
            if (token.isNullOrEmpty()) {
                // Không có token → quay về Login
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            } else {
                // Có token → khởi động Navigation
                setupNavigation()
            }
        }
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setupWithNavController(navController)
    }
}
