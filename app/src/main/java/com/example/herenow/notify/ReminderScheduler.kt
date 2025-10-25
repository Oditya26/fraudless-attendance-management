package com.example.herenow.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object ReminderScheduler {

    @SuppressLint("ScheduleExactAlarm")
    @RequiresApi(Build.VERSION_CODES.O)
    fun schedule15mBefore(
        context: Context,
        classId: Int,
        sessionNumber: Int,
        className: String,
        room: String,
        startDate: LocalDate,
        startTime: LocalTime?
    ) {
        if (startTime == null) return

        val triggerTimeMillis = startDate
            .atTime(startTime.minusMinutes(15))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("classId", classId)
            putExtra("sessionNumber", sessionNumber)
            putExtra("className", className)
            putExtra("room", room)
            putExtra("startTime", startTime.toString())
            putExtra("endTime", startTime.plusHours(2).toString()) // contoh default 2 jam
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            classId, // unique per class
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimeMillis,
            pendingIntent
        )
    }
}
