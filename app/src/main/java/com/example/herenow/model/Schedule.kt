package com.example.herenow.model

import java.time.LocalDate

data class Schedule(
    val date: LocalDate,
    val room: String,
    val courseCode: String,
    val time: String,
    val session: Int,
    val classId: Int? = null
)
