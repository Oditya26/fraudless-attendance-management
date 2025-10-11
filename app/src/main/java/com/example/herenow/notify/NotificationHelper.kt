package com.example.herenow.notify


import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build


object NotificationHelper {
    const val CHANNEL_ID = "class_session_reminders"
    private const val CHANNEL_NAME = "Class Session Reminders"
    private const val CHANNEL_DESC = "Reminders before class sessions start"


    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = CHANNEL_DESC }
            nm.createNotificationChannel(channel)
        }
    }
}