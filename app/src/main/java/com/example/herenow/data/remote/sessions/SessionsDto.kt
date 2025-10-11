package com.example.herenow.data.remote.sessions

data class SessionsResponse(
    val data: List<ClassWithSessions>
)

data class ClassWithSessions(
    val `class`: ClassInfo,
    val sessions: List<SessionItem>
)

data class ClassInfo(
    val ClassId: Int,
    val LecturerId: String,
    val CourseId: String,
    val ClassCode: String,
    val LecturerRoleId: Int,
    val ClassYear: Int,
    val Semester: Int,
    val NumberOfSession: Int,
    val CourseCategory: String,
    val created_at: String,
    val updated_at: String,
    val LecturerFullName: String,
    val CourseName: String
)

data class SessionItem(
    val SessionId: Int,
    val ClassId: Int,
    val SessionDate: String,   // "YYYY-MM-DD"
    val RoomId: String,
    val SessionNumber: Int,
    val Shift: Int,
    val created_at: String,
    val updated_at: String,
    val shift_start: String,   // "HH:mm:ss"
    val shift_end: String
)
