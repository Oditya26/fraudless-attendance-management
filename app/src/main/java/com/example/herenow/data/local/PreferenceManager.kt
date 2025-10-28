package com.example.herenow.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("here_now_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ATTENDANCE_STATUS = "attendance_status"
    }

    fun setAttendanceStatus(status: String) {
        prefs.edit().putString(KEY_ATTENDANCE_STATUS, status).apply()
    }

    fun getAttendanceStatus(): String? {
        return prefs.getString(KEY_ATTENDANCE_STATUS, null)
    }
}
