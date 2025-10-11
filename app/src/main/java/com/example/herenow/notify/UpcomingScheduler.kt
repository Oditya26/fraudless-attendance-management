package com.example.herenow.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.*
import java.time.format.DateTimeFormatter

object UpcomingScheduler {

    fun scheduleForToday(
        ctx: Context,
        sessions: List<SessionLite> // list sesi hari ini
    ) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        sessions.forEach { s ->
            val triggerAt = toTriggerEpochMillis(s.date, s.shiftStart, minusMinutes = 30)
            val now = System.currentTimeMillis()
            if (triggerAt <= now) return@forEach // sudah lewat, skip

            val intent = Intent(ctx, UpcomingClassReceiver::class.java).apply {
                putExtra("classId", s.classId)
                putExtra("room", s.room ?: "-")
                putExtra("title", s.title ?: s.course ?: "Class Reminder")
                putExtra("course", s.course ?: "-")
                putExtra("startTime", s.shiftStart.take(5))
            }
            val pi = PendingIntent.getBroadcast(
                ctx,
                s.classId, // requestCode unik per kelas
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Jadwalkan tepat waktu
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    data class SessionLite(
        val classId: Int,
        val date: LocalDate,
        val shiftStart: String, // "HH:mm:ss" atau "HH:mm"
        val room: String?,
        val title: String?,
        val course: String?
    )

    private fun toTriggerEpochMillis(
        date: LocalDate,
        start: String,
        minusMinutes: Long
    ): Long {
        val lt = runCatching { LocalTime.parse(start) }
            .getOrElse { LocalTime.parse(start.take(5) + ":00") } // toleransi "HH:mm"
        val ldt = LocalDateTime.of(date, lt).minusMinutes(minusMinutes)
        val zdt = ldt.atZone(ZoneId.systemDefault())
        return zdt.toInstant().toEpochMilli()
    }
}
