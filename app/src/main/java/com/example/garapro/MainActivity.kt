package com.example.garapro

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.example.garapro.data.local.TokenManager
import com.example.garapro.data.model.UpdateDeviceIdRequest
import com.example.garapro.data.model.emergencies.EmergencySoundPlayer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.material.badge.BadgeDrawable

class MainActivity : AppCompatActivity(), TokenExpiredListener {

    companion object {
        private const val TAG = "MainActivity"
    }
    private lateinit var tokenManager: TokenManager
    private lateinit var navController: NavController
    private var destinationChangedListener: NavController.OnDestinationChangedListener? = null
    private var notificationBadge: BadgeDrawable? = null

    private var emergencyReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags("en")
        )

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e("AppCrash", "Uncaught: ${e.message}", e)
        }


        tokenManager = TokenManager(this)

        RetrofitInstance.initialize(tokenManager, this)


        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController


        lifecycleScope.launch {
            val token = tokenManager.getAccessTokenSync()
            if (token.isNullOrEmpty()) {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
                return@launch
            }

            val role = tokenManager.getUserRole()
            setupNavigationByRole(role)

            val hasNotification = intent?.extras?.isEmpty == false
            if (hasNotification) {
                handleIntent(intent)    //  đừng navigate Home trước khi xử lý noti
            } else {
                val role = tokenManager.getUserRole() // lấy role bạn lưu khi login
                setupNavigationByRole(role)

                setupNotificationBadge()
                startUnreadCountPolling()

                // Xử lý intent sau khi setup navigation
                handleIntent(intent)
            }
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                updateDeviceIdToServer(token)
                Log.d("DeviceToken", "Current token: $token")
            } else {
                Log.w("DeviceToken", "Fetching FCM token failed", task.exception)
            }
        }

        requestNotificationPermission()
        registerEmergencyReceiver()
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
        EmergencySoundPlayer.stop()

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



    private fun determineNavigation(
        screen: String?,
        notificationType: String?,
        action: String?,
        ids: Map<String, String>
    ): NavigationInfo {

        return when {

            notificationType == "Emergency" && screen == "ReportsFragment" -> {
                Log.d("Navigation", "Emergency navigation to ReportsFragment")
                NavigationInfo(R.id.reportsFragment, ids, "emergency")
            }
            // Case 1: Appointment được chấp nhận
            screen == "QuotationDetailFragment" && ids.containsKey("quotationId") ->
            {
                Log.d("quo","quoday")
                NavigationInfo(R.id.quotationDetailFragment, ids, "quotationId")
            }

            // Case 2: Repair progress được cập nhật
            screen == "RepairProgressDetailFragment" && ids.containsKey("repairOrderId") ->
            {
                Log.d("quo","Repair")

                NavigationInfo(R.id.repairProgressDetailFragment, ids, "repair_updated")

            }

            screen == "RepairOrderArchivedDetailFragment" && ids.containsKey("repairOrderId") ->
            {
                Log.d("quo","ArchivedDetailFragment")

                NavigationInfo(R.id.repairArchivedDetailFragment, ids, "repair_updated")

            }
            screen == "RepairRequestDetailFragment" && ids.containsKey("repairRequestId") ->
            {


                NavigationInfo(R.id.appointmentDetailFragment, ids, "repair_updated")

            }

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

            if (navController.graph.id != targetGraph) {
                navController.graph = navController.navInflater.inflate(targetGraph)
            }

            val bundle = Bundle().apply {
                navigationInfo.ids.forEach { (key, value) ->
                    putString(key, value)
                }
                putString("notificationType", navigationInfo.type)
                putBoolean("fromNotification", true)
            }

            when (navigationInfo.destinationId) {
                // 🔹 Notification QUOTATION → tab Appointments + detail
                R.id.quotationDetailFragment -> {
                    try {
                        // 1. Vào graph Appointments => BottomNav tự chọn tab Appointments
                        navController.navigate(R.id.appointmentGraph)
                    } catch (_: Exception) {
                        // nếu đã ở trong appointmentGraph rồi thì ignore
                    }

                    // 2. Mở QuotationDetail
                    navController.navigate(R.id.quotationDetailFragment, bundle)
                }

                // 🔹 Notification REPAIR → tab Repair + detail
                R.id.repairProgressDetailFragment -> {
                    try {
                        // 1. Vào graph RepairTracking
                        navController.navigate(R.id.repairTrackingGraph)
                    } catch (_: Exception) { }

                    // 2. Mở RepairProgressDetail
                    navController.navigate(R.id.repairProgressDetailFragment, bundle)
                }
                R.id.repairArchivedDetailFragment -> {
                    try {
                        // 1. Vào graph repairArchivedGraph
                        navController.navigate(R.id.repairArchivedGraph)
                    } catch (_: Exception) { }

                    // 2. Mở RepairProgressDetail
                    navController.navigate(R.id.repairArchivedDetailFragment, bundle)
                }

                R.id.appointmentDetailFragment -> {
                    try {

                        navController.navigate(R.id.appointmentGraph)
                    } catch (_: Exception) { }


                    navController.navigate(R.id.appointmentDetailFragment, bundle)
                }

                else -> {
                    navController.navigate(navigationInfo.destinationId, bundle)
                }
            }

        } catch (e: Exception) {
            Log.e("Navigation", "Failed to navigate: ${e.message}")
            navigateToHome()
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

        // 1. Chọn graph + menu theo role
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

        // 2. Bỏ listener cũ nếu có
        destinationChangedListener?.let {
            navController.removeOnDestinationChangedListener(it)
        }

        // 3. Tự handle click bottom nav (KHÔNG dùng setupWithNavController nữa)
        bottomNavigation.setOnItemSelectedListener { item ->
            val navOptions = NavOptions.Builder()
                .setPopUpTo(navController.graph.startDestinationId, false)
                .setLaunchSingleTop(true)
                .build()

            when (role) {
                "Technician" -> {
                    when (item.itemId) {
                        R.id.technicianFragment -> {
                            navController.navigate(R.id.technicianFragment, null, navOptions)
                            true
                        }
                        R.id.reportsFragment -> {
                            navController.navigate(R.id.reportsFragment, null, navOptions)
                            true
                        }
                        R.id.notificationsFragment -> {
                            navController.navigate(R.id.notificationsFragment, null, navOptions)
                            true
                        }
                        R.id.profileFragment -> {
                            navController.navigate(R.id.profileFragment, null, navOptions)
                            true
                        }
                        else -> false
                    }
                }

                else -> {
                    // CUSTOMER ROLE
                    when (item.itemId) {
                        R.id.homeFragment -> {
                            navController.navigate(R.id.homeFragment, null, navOptions)
                            true
                        }

                        R.id.appointmentGraph -> {
                            navController.navigate(R.id.appointmentGraph, null, navOptions)
                            true
                        }

                        R.id.repairTrackingGraph -> {
                            navController.navigate(R.id.repairTrackingGraph, null, navOptions)
                            true
                        }

                        R.id.repairArchivedGraph -> {
                            navController.navigate(R.id.repairArchivedGraph, null, navOptions)
                            true
                        }

                        R.id.chat -> {
                            navController.navigate(R.id.chat, null, navOptions)
                            true
                        }

                        R.id.profileFragment -> {
                            navController.navigate(R.id.profileFragment, null, navOptions)
                            true
                        }

                        else -> false
                    }
                }
            }
        }

        // 4. Listener sync checked state theo destination hiện tại
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            when (role) {
                "Technician" -> {
                    when (destination.id) {
                        R.id.technicianFragment -> {
                            bottomNavigation.menu.findItem(R.id.technicianFragment)?.isChecked = true
                        }
                        R.id.reportsFragment -> {
                            bottomNavigation.menu.findItem(R.id.reportsFragment)?.isChecked = true
                        }
                        R.id.notificationsFragment -> {
                            bottomNavigation.menu.findItem(R.id.notificationsFragment)?.isChecked = true
                        }
                        R.id.profileFragment -> {
                            bottomNavigation.menu.findItem(R.id.profileFragment)?.isChecked = true
                        }
                    }
                }

                else -> {
                    when (destination.id) {
                        // HOME
                        R.id.homeFragment -> {
                            bottomNavigation.menu.findItem(R.id.homeFragment)?.isChecked = true
                        }

                        // APPOINTMENTS / QUOTATIONS (tab Appointment)
                        R.id.appointmentNavFragment,
                        R.id.appointmentsFragment,
                        R.id.appointmentDetailFragment,
                        R.id.quotationsFragment,
                        R.id.quotationDetailFragment -> {
                            bottomNavigation.menu.findItem(R.id.appointmentGraph)?.isChecked = true
                        }

                        // REPAIR TRACKING
                        R.id.repairTrackingFragment,
                        R.id.repairProgressDetailFragment -> {
                            bottomNavigation.menu.findItem(R.id.repairTrackingGraph)?.isChecked = true
                        }

                        // REPAIR ARCHIVED
                        R.id.repairArchivedFragment,
                        R.id.repairArchivedDetailFragment -> {
                            bottomNavigation.menu.findItem(R.id.repairArchivedGraph)?.isChecked = true
                        }

                        // PROFILE
                        R.id.profileFragment -> {
                            bottomNavigation.menu.findItem(R.id.profileFragment)?.isChecked = true
                        }
                    }
                }
            }
        }

        navController.addOnDestinationChangedListener(listener)
        destinationChangedListener = listener
    }




    private fun updateDeviceIdToServer(deviceToken: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("go","gogo")
                val request = UpdateDeviceIdRequest(deviceId = deviceToken)
                val response = RetrofitInstance.UserService.updateDeviceId(request)

                if (response.isSuccessful) {
                    Log.d("DeviceToken", "Device ID updated successfully")
                } else {
                    Log.e("DeviceToken", "Failed to update device ID: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("DeviceToken", "Error updating device ID: ${e.message}")
            }
        }
    }
    private fun setupNotificationBadge() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        notificationBadge = bottomNav.getOrCreateBadge(R.id.notificationsFragment)
        notificationBadge?.apply {
            backgroundColor = getColor(R.color.red)
            badgeTextColor = getColor(R.color.white)
            isVisible = false
            maxCharacterCount = 3
        }
    }

    private fun startUnreadCountPolling() {
        loadUnreadCount()

        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5_000)
            }
        }
    }

    private fun loadUnreadCount() {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.notificationService.getUnreadCount()

                if (response.isSuccessful) {
                    response.body()?.let { unreadCountResponse ->
                        updateBadge(unreadCountResponse.unreadCount)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading unread count: ${e.message}")
            }
        }
    }

    private fun updateBadge(count: Int) {
        runOnUiThread {
            notificationBadge?.apply {
                if (count > 0) {
                    isVisible = true
                    number = count
                } else {
                    isVisible = false
                }
            }
        }
    }

    fun updateNotificationBadge(count: Int) {
        updateBadge(count)
    }

    override fun onTokenExpired() {
        runOnUiThread {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
    override fun onResume() {
        super.onResume()
        loadUnreadCount()
    }
    private fun navigateToHome() {
        try {
            navController.navigate(R.id.homeFragment)
        } catch (e: Exception) {
            Log.e("Navigation", "Failed to navigate to home: ${e.message}")
        }
    }

    private fun registerEmergencyReceiver() {
        if (emergencyReceiver != null) return

        emergencyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val title = intent?.getStringExtra("title") ?: "Emergency"
                val body = intent?.getStringExtra("body") ?: ""
                val screen = intent?.getStringExtra("screen") ?: "ReportsFragment"

                // Ở foreground: NotificationService đã start chuông + rung rồi
                showEmergencyDialog(title, body, screen)
            }
        }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(emergencyReceiver!!,
                IntentFilter("com.example.garapro.EMERGENCY_EVENT")
            )
    }

    private fun showEmergencyDialog(title: String, body: String, screen: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->

                EmergencySoundPlayer.stop()


                val i = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP

                    putExtra("screen", "ReportsFragment")
                    putExtra("notificationType", "Emergency")
                    putExtra("from_notification", true)
                }
                startActivity(i)
            }
            .show()
    }





    override fun onDestroy() {
        super.onDestroy()
        emergencyReceiver?.let {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
        }
    }

}