package com.example.garapro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.remote.RetrofitInstance
import com.example.garapro.data.remote.TokenExpiredListener
import com.example.garapro.ui.home.NavigationInfo
import com.example.garapro.ui.login.LoginActivity
import com.example.garapro.ui.paymentResults.PaymentResultActivity
import com.example.garapro.utils.Constants
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), TokenExpiredListener {

    companion object {
        private const val TAG = "MainActivity"
    }
    private lateinit var tokenManager: TokenManager
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenManager = TokenManager(this)
        // 🔹 Khởi tạo RetrofitInstance ở đây
        RetrofitInstance.initialize(tokenManager, this)

        // Khởi tạo navController
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Kiểm tra token khi khởi động
        lifecycleScope.launch {
            val token = tokenManager.getAccessTokenSync()
            if (token.isNullOrEmpty()) {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            } else {
                val role = tokenManager.getUserRole() // lấy role bạn lưu khi login
                setupNavigationByRole(role)
                // Xử lý intent sau khi setup navigation
                handleIntent(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
//        handleDeepLink(intent)
    }

    private fun handleIntent(intent: Intent) {
        // Extract tất cả possible IDs từ intent
        val allIds = extractAllIds(intent)
        val screen = intent.getStringExtra("screen")
        val notificationType = intent.getStringExtra("notificationType")
        val action = intent.getStringExtra("action")

        Log.d("Notification", "Handling - screen: $screen, type: $notificationType, action: $action, ids: $allIds")

        // Xác định destination dựa trên sự kết hợp của các tham số
        val navigationInfo = determineNavigation(screen, notificationType, action, allIds)

        lifecycleScope.launch {
            executeNavigation(navigationInfo)
        }
    }

//    private fun handleDeepLink(intent: Intent) {
//        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
//            val data = intent.data!!
//            Log.d(TAG, "Handling Deep Link: $data")
//
//            // Chuyển hướng đến PaymentResultActivity nếu là link payment
//            if (data.scheme == "myapp" && data.host == "payment") {
//                val paymentIntent = Intent(this, PaymentResultActivity::class.java)
//                paymentIntent.data = data
//                startActivity(paymentIntent)
//                finish() // Kết thúc MainActivity nếu cần
//            } else {
//                // Xử lý các deep link khác tại đây
//                handleOtherDeepLinks(data)
//            }
//        }
//    }
    private fun handleOtherDeepLinks(uri: Uri) {
        // Xử lý các deep link khác không phải payment
        when {
            uri.host == "profile" -> {
                // Mở profile
            }
            uri.host == "booking" -> {
                // Mở booking
            }
            // Thêm các case khác...
        }
    }

    private fun determineNavigation(
        screen: String?,
        notificationType: String?,
        action: String?,
        ids: Map<String, String>
    ): NavigationInfo {

        return when {
            // Case 1: Appointment được chấp nhận
            screen == "QuotationDetailFragment" && ids.containsKey("quotationId") ->
            {
                Log.d("quo","quoday")
                NavigationInfo(R.id.quotationDetailFragment, ids, "quotationId")
            }

            // Case 2: Repair progress được cập nhật
            screen == "RepairProgressDetailFragment" && ids.containsKey("repairOrderId") ->
                NavigationInfo(R.id.repairProgressDetailFragment, ids, "repair_updated")

            // Case 3: Payment thông báo
//            notificationType == "payment_completed" && ids.containsKey("paymentId") ->
//                NavigationInfo(R.id.paymentStatusFragment, ids, "payment_done")

            // Case 4: Chat message
//            action == "new_message" && ids.containsKey("chatId") ->
//                NavigationInfo(R.id.chatFragment, ids, "new_message")
//
//            // Case 5: Dựa trên sự kết hợp của các IDs
//            ids.containsKey("invoiceId") && ids.containsKey("paymentId") ->
//                NavigationInfo(R.id.invoiceDetailFragment, ids, "invoice_payment")

            // Thêm các case khác...
            else -> {
                Log.d("quo","home")

                NavigationInfo(R.id.homeFragment, ids, "default")}
        }
    }

    private suspend fun executeNavigation(navigationInfo: NavigationInfo) {
        try {
            val role = tokenManager.getUserRole()
            val targetGraph = when (role) {
                "Technician" -> R.navigation.nav_technician
                else -> R.navigation.nav_customer
            }

            // Đảm bảo đúng graph
            if (navController.graph.id != targetGraph) {
                navController.graph = navController.navInflater.inflate(targetGraph)
            }

            val parentMenuItemId = getParentMenuItemId(navigationInfo.destinationId)
            val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
            bottomNavigation.selectedItemId = parentMenuItemId

            // Tạo bundle với tất cả IDs
            val bundle = Bundle().apply {
                navigationInfo.ids.forEach { (key, value) ->
                    putString(key, value)
                }
                putString("notificationType", navigationInfo.type)
                putBoolean("fromNotification", true)
            }

            // Navigate với clear back stack
            val navOptions = NavOptions.Builder()
                .setPopUpTo(navController.graph.startDestinationId, false)
                .build()

            navController.navigate(navigationInfo.destinationId, bundle, navOptions)

        } catch (e: Exception) {
            Log.e("Navigation", "Failed to navigate: ${e.message}")
            navigateToHome()
        }
    }

    private fun getParentMenuItemId(destinationId: Int): Int {
        return when (destinationId) {
            R.id.quotationDetailFragment, R.id.quotationsFragment -> R.id.appointmentNavFragment
            R.id.repairProgressDetailFragment -> R.id.repairTrackingFragment
            else -> destinationId // Nếu là fragment chính thì dùng chính nó
        }
    }
    private fun extractAllIds(intent: Intent): Map<String, String> {
        val idMap = mutableMapOf<String, String>()

        // Danh sách tất cả các key ID có thể có
        val possibleIdKeys = listOf(
            "repairRequestId", "repairOrderId", "quotationId", "bookingId",
            "appointmentId", "serviceId", "technicianId", "customerId",
            "paymentId", "invoiceId", "chatId", "messageId",
            "quoteId", "estimateId", "taskId"
        )

        possibleIdKeys.forEach { key ->
            intent.getStringExtra(key)?.let { value ->
                if (value.isNotEmpty()) {
                    idMap[key] = value
                }
            }
        }

        return idMap
    }

    private fun setupNavigationByRole(role: String?) {
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
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
    private fun navigateToHome() {
        try {
            navController.navigate(R.id.homeFragment)
        } catch (e: Exception) {
            Log.e("Navigation", "Failed to navigate to home: ${e.message}")
        }
    }
}