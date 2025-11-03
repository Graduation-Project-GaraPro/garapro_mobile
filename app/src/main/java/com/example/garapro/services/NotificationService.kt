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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class NotificationService: FirebaseMessagingService() {

    private val CHANNEL_ID = "my_channel_id"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("DeviceToken", "Refreshed token: $token")
        // TODO: Send token to your server
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

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
            val orderId = remoteMessage.data["order_id"]

            // 4️⃣ Tạo Intent mở MainActivity kèm dữ liệu
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("screen", screen)
                putExtra("order_id", orderId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 5️⃣ Tạo Notification
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_camera) // đổi icon theo project
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            NotificationManagerCompat.from(this)
                .notify(orderId?.hashCode() ?: System.currentTimeMillis().toInt(), builder.build())
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
}