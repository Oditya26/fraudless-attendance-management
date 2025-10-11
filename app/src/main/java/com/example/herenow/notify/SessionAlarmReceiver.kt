package com.example.herenow.notify


import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.herenow.R


class SessionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.ensureChannel(context)


        val title = intent.getStringExtra("title") ?: "Kelas akan dimulai"
        val room = intent.getStringExtra("room") ?: "-"
        val time = intent.getStringExtra("time") ?: "-"
        val classId = intent.getIntExtra("classId", 0)
        val sessionNumber = intent.getIntExtra("sessionNumber", 0)


        val content = "Ruang $room · $time"
        val notificationId = (classId * 1000) + sessionNumber


        val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_teacher)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)


        val nm = NotificationManagerCompat.from(context)


// Android 13+ requires POST_NOTIFICATIONS runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w("SessionAlarmReceiver", "No POST_NOTIFICATIONS permission; skipping notify.")
                return
            }
        }


        if (!nm.areNotificationsEnabled()) return


        try {
            nm.notify(notificationId, builder.build())
        } catch (se: SecurityException) {
            Log.w("SessionAlarmReceiver", "Notify blocked by SecurityException", se)
        }
    }
}