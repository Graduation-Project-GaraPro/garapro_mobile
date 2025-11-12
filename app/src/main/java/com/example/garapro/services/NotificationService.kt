package com.example.garapro.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.garapro.MainActivity
import com.example.garapro.R
import com.example.garapro.data.model.UpdateDeviceIdRequest
import com.example.garapro.data.remote.RetrofitInstance
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationService : FirebaseMessagingService() {

    private val CHANNEL_ID = "my_channel_id"

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.

        //sendRegistrationToServer(token)
        updateDeviceIdToServer(token)
        Log.d("DeviceToken", "Refreshed token: $token")
        // TODO: Send token to your server
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("Notification", "Message received:  ${remoteMessage.toString()}${remoteMessage.data}")

        // 1️⃣ Tạo Notification Channel nếu chưa tồn tại
        createNotificationChannel()

        // 2️⃣ Check permission Android 13+
        val canNotify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!canNotify) {
            Log.w("Notification", "Notification permission not granted")
            return
        }

        // 3️⃣ Lấy dữ liệu từ data message
        if (remoteMessage.data.isNotEmpty()) {
            val title = remoteMessage.data["title"] ?: "Thông báo"
            val body = remoteMessage.data["body"] ?: ""
            val screen = remoteMessage.data["screen"]
            val notificationType = remoteMessage.data["type"]
            val action = remoteMessage.data["action"]
            Log.d("screen",screen.toString())
            // 4️⃣ Extract tất cả IDs từ remoteMessage
            val allIds = extractAllIdsFromMessage(remoteMessage.data)

            // 5️⃣ Tạo Intent mở MainActivity kèm dữ liệu
            val intent = Intent(this, MainActivity::class.java).apply {
                // Clear any existing tasks and start fresh
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                // Put extras chính
                putExtra("screen", screen)
                putExtra("type", notificationType)
                putExtra("body", body)
                putExtra("action", action)
                putExtra("from_notification", true)

                // Put tất cả IDs
                allIds.forEach { (key, value) ->
                    putExtra(key, value)
                }
                Log.d("allIds",allIds.toString())

            }

            // 6️⃣ Tạo unique ID cho notification dựa trên tất cả IDs
            val notificationId = generateNotificationId(allIds)

            val pendingIntent = PendingIntent.getActivity(
                this,
                notificationId, // Unique request code
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 7️⃣ Tạo Notification
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications) // đổi icon theo project
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // Tự động đóng khi nhấp
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            // Thêm style cho notification dài
            if (body.length > 50) {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }

            NotificationManagerCompat.from(this)
                .notify(notificationId, builder.build())

            Log.d("Notification", "Notification shown with ID: $notificationId, IDs: $allIds")
        }
    }

    private fun extractAllIdsFromMessage(data: Map<String, String>): Map<String, String> {
        val idMap = mutableMapOf<String, String>()

        // Danh sách tất cả các key ID có thể có (đồng bộ với MainActivity)
        val possibleIdKeys = listOf(
            "repairRequestId", "repairOrderId", "orderId", "quotationId",
            "appointmentId", "serviceId", "technicianId", "customerId",
            "paymentId", "invoiceId", "chatId", "messageId",
            "quoteId", "estimateId", "taskId", "requestId"
        )

        possibleIdKeys.forEach { key ->
            data[key]?.let { value ->
                if (value.isNotEmpty()) {
                    idMap[key] = value
                }
            }
        }

        return idMap
    }

    private fun generateNotificationId(ids: Map<String, String>): Int {
        // Tạo ID duy nhất dựa trên tất cả IDs
        val combinedString = ids.entries
            .sortedBy { it.key }
            .joinToString("|") { "${it.key}=${it.value}" }

        return if (combinedString.isNotEmpty()) {
            combinedString.hashCode() and 0x7fffffff // Đảm bảo positive
        } else {
            System.currentTimeMillis().toInt() and 0x7fffffff
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "My Notifications"
            val descriptionText = "Thông báo từ app"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
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
}