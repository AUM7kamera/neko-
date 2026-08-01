package com.example.disasterapp.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.disasterapp.R
import com.example.disasterapp.ui.MainActivity

object NotificationUtils {
    private const val CHANNEL_EMERGENCY_ID = "emergency_channel"
    private const val CHANNEL_DEFAULT_ID = "default_channel"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)

            val emergency = NotificationChannel(
                CHANNEL_EMERGENCY_ID,
                "緊急通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "警報・緊急通知用チャンネル"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val normal = NotificationChannel(
                CHANNEL_DEFAULT_ID,
                "一般通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "通常通知" }

            nm.createNotificationChannel(emergency)
            nm.createNotificationChannel(normal)
        }
    }

    fun showEmergencyNotification(context: Context, title: String, text: String, notificationId: Int = 1001) {
        createChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_EMERGENCY_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }
}
