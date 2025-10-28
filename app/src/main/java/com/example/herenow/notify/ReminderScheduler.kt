package com.example.herenow.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ReminderScheduler {

    @SuppressLint("ScheduleExactAlarm")
    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleRepeatingReminder(
        context: Context,
        classId: Int,
        sessionNumber: Int,
        className: String,
        room: String,
        startDate: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?
    ) {
        if (startTime == null || endTime == null) return

        val zone = ZoneId.systemDefault()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 🔹 Mulai 15 menit sebelum kelas
        val startTriggerMillis = startDate
            .atTime(startTime.minusMinutes(15))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("classId", classId)
            putExtra("sessionNumber", sessionNumber)
            putExtra("className", className)
            putExtra("room", room)
            putExtra("startTime", startTime.toString())
            putExtra("endTime", endTime.toString())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            classId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🔹 Jadwalkan notifikasi pertama
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            startTriggerMillis,
            pendingIntent
        )

        Log.d("ReminderScheduler", "🔔 Reminder pertama dijadwalkan pada: ${startTime.minusMinutes(15)}")

        // 🔹 Ulangi tiap 1 menit
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            startTriggerMillis,
            60_000L, // 1 menit
            pendingIntent
        )
    }

    fun cancelReminder(context: Context, classId: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            classId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        Log.d("ReminderScheduler", "⏹️ Reminder dibatalkan untuk classId=$classId")
    }
}
