// app/src/main/java/com/example/herenow/model/AttendanceModels.kt
package com.example.herenow.model

enum class AttendanceStatus { ATTENDED, NOT_ATTENDED }

data class AttendanceKey(
    val courseCode: String,
    val session: Int
)
