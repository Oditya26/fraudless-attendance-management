package com.example.herenow.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.herenow.LoginActivity
import com.example.herenow.R

object NotificationHelper {

    private const val CHANNEL_ID = "class_reminder_channel"
    private const val CHANNEL_NAME = "Class Reminders"
    private const val CHANNEL_DESC = "Reminds you about upcoming classes"

    // 🔹 Fungsi untuk memastikan channel notifikasi sudah dibuat (wajib di Android 8+)
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 🔹 Fungsi utama untuk menampilkan notifikasi expandable + intent ke LoginActivity
    fun showExpandableNotification(context: Context, title: String, message: String) {
        // Pastikan channel sudah dibuat
        ensureChannel(context)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_teacher)
            .setContentTitle(title)
            .setContentText("Tap to view more details")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    android.text.Html.fromHtml(
                        message,
                        android.text.Html.FROM_HTML_MODE_LEGACY
                    )
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
