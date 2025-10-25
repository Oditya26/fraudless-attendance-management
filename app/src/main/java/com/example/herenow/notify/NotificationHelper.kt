package com.example.herenow.notify

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.text.HtmlCompat
import com.example.herenow.LoginActivity
import com.example.herenow.R

object NotificationHelper {

    private const val CHANNEL_ID = "class_reminder_channel"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Class Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you about upcoming classes"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showSimpleNotification(
        context: Context,
        title: String,
        message: String
    ) {
        ensureChannel(context)

        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_teacher)
            .setContentTitle(title)
            .setContentText(HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(context)

        // ✅ Cek izin POST_NOTIFICATIONS sebelum menampilkan notifikasi
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } else {
            Log.w("NotificationHelper", "Permission POST_NOTIFICATIONS not granted. Notification skipped.")
        }
    }
}
