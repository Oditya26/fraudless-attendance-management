package com.example.herenow.notify

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.example.herenow.DetailFragment
import com.example.herenow.R

class UpcomingClassReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Pastikan channel ada
        NotificationHelper.ensureChannel(context)

        val classId   = intent.getIntExtra("classId", 0)
        val room      = intent.getStringExtra("room") ?: "-"
        val title     = intent.getStringExtra("title") ?: "Class Reminder"
        val course    = intent.getStringExtra("course") ?: "-"
        val startTime = intent.getStringExtra("startTime") ?: ""

        // Intent ke activity host (ganti ke MainActivity bila perlu)
        val activityIntent = Intent(context, Class.forName("com.example.herenow.LoginActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtras(Bundle().apply {
                putInt(DetailFragment.ARG_CLASS_ID, classId)
                putString(DetailFragment.ARG_ROOM, room)
                putString(DetailFragment.ARG_CODE, course)
                putString(DetailFragment.ARG_TITLE, title)
            })
        }

        val pendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(activityIntent)
            getPendingIntent(
                classId, // unik per kelas
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Gunakan icon yang pasti ada. Anda bisa ganti ke R.drawable.ic_notification jika sudah ditambahkan.
        val smallIconRes = R.mipmap.ic_launcher

        val notif = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle("Class in 30 minutes")
            .setContentText("$course • $room • $startTime")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$title\n$course • $room • $startTime")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Cek izin POST_NOTIFICATIONS untuk Android 13+ sebelum notify
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                // Tidak punya izin → jangan crash, cukup keluar
                return
            }
        }

        try {
            NotificationManagerCompat.from(context).notify(classId, notif)
        } catch (se: SecurityException) {
            // Graceful: abaikan jika ada race condition permission
        }
    }
}
