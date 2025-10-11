package com.example.herenow.model

data class User(
    val email: String,
    var password: String,
    val name: String,
    val nim: String,

    val enrollments: MutableList<EnrollmentCourse> = mutableListOf(),
    val schedules: MutableList<Schedule> = mutableListOf(),

    val attendance: MutableMap<AttendanceKey, AttendanceStatus> = mutableMapOf()
)
