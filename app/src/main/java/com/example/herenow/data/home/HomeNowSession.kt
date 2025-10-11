package com.example.herenow.data.home

data class HomeNowSession(
    val classId: Int,
    val classCode: String?,
    val courseName: String?,
    val courseCategory: String?,
    val lecturerFullName: String?,
    val credit: Int?,
    val roomId: String?,
    val sessionDate: String,    // "YYYY-MM-DD"
    val sessionNumber: Int,
    val shift: Int?,
    val shiftStart: String,     // "HH:mm:ss"
    val shiftEnd: String,       // "HH:mm:ss"
    val attended: Boolean       // true jika semua flag = 1 pada salah satu presence
)
