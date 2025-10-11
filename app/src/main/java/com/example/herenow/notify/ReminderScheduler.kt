package com.example.herenow.notify


import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import java.time.*


object ReminderScheduler {
    @SuppressLint("ScheduleExactAlarm")
    @RequiresApi(Build.VERSION_CODES.O)
    fun schedule15mBefore(
        context: Context,
        classId: Int,
        sessionNumber: Int,
        title: String?,
        room: String?,
        startDate: LocalDate?,
        startTime: LocalTime?,
    ) {
        if (startDate == null || startTime == null) return
        val zone = ZoneId.systemDefault()
        val startDateTime = ZonedDateTime.of(startDate, startTime, zone)
        val triggerAt = startDateTime.minusMinutes(15)
        val now = ZonedDateTime.now(zone)
        if (!triggerAt.isAfter(now)) return // jangan jadwalkan kalau sudah lewat


        val reqCode = (classId * 1000) + sessionNumber
        val fireIntent = Intent(context, SessionAlarmReceiver::class.java).apply {
            putExtra("classId", classId)
            putExtra("sessionNumber", sessionNumber)
            putExtra("title", title ?: "Kelas akan dimulai dalam 15 menit")
            putExtra("room", room ?: "-")
            putExtra("time", "${startTime.toString().take(5)} WIB")
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val firePI = PendingIntent.getBroadcast(context, reqCode, fireIntent, piFlags)


        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMillis = triggerAt.toInstant().toEpochMilli()


// --- Android 12+ (API 31): pakai setAlarmClock agar TIDAK butuh SCHEDULE_EXACT_ALARM ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
// showIntent: intent yang muncul saat user tap indikator alarm (buka app)
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent().setPackage(context.packageName)
            val showPI = PendingIntent.getActivity(
                context,
                reqCode,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            val info = AlarmManager.AlarmClockInfo(triggerMillis, showPI)
            am.setAlarmClock(info, firePI)
            return
        }


// --- Android < 12 ---
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, firePI)
            else ->
                am.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, firePI)
        }
    }
}