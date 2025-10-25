package com.example.herenow.notify

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission

class ReminderReceiver : BroadcastReceiver() {

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {
        val classId = intent.getIntExtra("classId", -1)
        val sessionNumber = intent.getIntExtra("sessionNumber", -1)
        val className = intent.getStringExtra("className") ?: "Upcoming Class"
        val room = intent.getStringExtra("room") ?: "Unknown Room"
        val startTime = intent.getStringExtra("startTime") ?: "-"
        val endTime = intent.getStringExtra("endTime") ?: "-"

        val notificationTitle = "Class Schedule Reminder!"
        val notificationMessage = """
            <b>Course Name :</b> $className<br>
            <b>Session :</b> $sessionNumber<br>
            <b>Duration :</b> $startTime - $endTime<br>
            <b>Room Code :</b> $room
        """.trimIndent()

        NotificationHelper.showSimpleNotification(
            context = context,
            title = notificationTitle,
            message = notificationMessage
        )
    }
}
