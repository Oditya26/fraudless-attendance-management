package com.example.herenow.util

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

object AlarmPermissionHelper {

    fun hasExactAlarmPermission(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // No permission required below Android 12
        }
    }

    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun showPermissionDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Allow Exact Alarm Permission")
            .setMessage(
                "This app needs permission to schedule alarms accurately. " +
                        "Without this permission, some features like reminders or notifications may not work properly."
            )
            .setPositiveButton("Allow") { _, _ ->
                requestExactAlarmPermission(context)
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }
}
