package com.example.herenow.notify

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.herenow.data.local.PreferenceManager
import java.time.LocalTime

class ReminderReceiver : BroadcastReceiver() {

    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {
        val classId = intent.getIntExtra("classId", -1)
        val sessionNumber = intent.getIntExtra("sessionNumber", -1)
        val className = intent.getStringExtra("className") ?: "Upcoming Class"
        val room = intent.getStringExtra("room") ?: "Unknown Room"
        val startTime = intent.getStringExtra("startTime") ?: "-"
        val endTime = intent.getStringExtra("endTime") ?: "-"

        val prefs = PreferenceManager(context)
        val status = prefs.getAttendanceStatus()

        // 🔹 Jika user sudah "waiting" atau "attended", hentikan notifikasi
        if (status == "Waiting for Verification" || status == "Attended") {
            ReminderScheduler.cancelReminder(context, classId)
            return
        }

        // 🔹 Jika sudah lewat waktu kelas, hentikan juga
        try {
            val now = LocalTime.now()
            val end = LocalTime.parse(endTime)
            if (now.isAfter(end)) {
                ReminderScheduler.cancelReminder(context, classId)
                return
            }
        } catch (_: Exception) { }

        // 🔹 Tampilkan notifikasi expandable
        val notificationTitle = "Class Schedule Reminder!"
        val notificationMessage = """
            <b>Course Name :</b> $className<br>
            <b>Session :</b> $sessionNumber<br>
            <b>Duration :</b> $startTime - $endTime<br>
            <b>Room Code :</b> $room
        """.trimIndent()

        NotificationHelper.showExpandableNotification(
            context = context,
            title = notificationTitle,
            message = notificationMessage
        )
    }
}
