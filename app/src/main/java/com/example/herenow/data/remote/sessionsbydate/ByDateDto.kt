package com.example.herenow.data.remote.sessionsbydate

import com.google.gson.annotations.SerializedName

data class ByDateResponse(
    @SerializedName("data") val data: List<ByDateItem> = emptyList()
)

data class ByDateItem(
    @SerializedName("class")  val clazz: ByDateClassDto,
    @SerializedName("session") val session: ByDateSessionDto,
    @SerializedName("presences") val presences: List<ByDatePresenceDto> = emptyList()
)

data class ByDateClassDto(
    @SerializedName("ClassId") val classId: Int,
    @SerializedName("LecturerId") val lecturerId: String?,
    @SerializedName("CourseId") val courseId: String?,
    @SerializedName("ClassCode") val classCode: String?,
    @SerializedName("LecturerRoleId") val lecturerRoleId: Int?,
    @SerializedName("ClassYear") val classYear: Int?,
    @SerializedName("Semester") val semester: Int?,
    @SerializedName("NumberOfSession") val numberOfSession: Int?,
    @SerializedName("CourseCategory") val courseCategory: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("LecturerFullName") val lecturerFullName: String?,
    @SerializedName("CourseName") val courseName: String?,
    @SerializedName("Credit") val credit: Int?
)

data class ByDateSessionDto(
    @SerializedName("SessionId") val sessionId: Int,
    @SerializedName("ClassId") val classId: Int,
    @SerializedName("SessionDate") val sessionDate: String, // "YYYY-MM-DD"
    @SerializedName("RoomId") val roomId: String?,
    @SerializedName("SessionNumber") val sessionNumber: Int,
    @SerializedName("Shift") val shift: Int?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("shift_start") val shiftStart: String,  // "HH:mm:ss"
    @SerializedName("shift_end") val shiftEnd: String       // "HH:mm:ss"
)

data class ByDatePresenceDto(
    @SerializedName("PresenceId") val presenceId: Int,
    @SerializedName("SessionId") val sessionId: Int,
    @SerializedName("StudentId") val studentId: String?,
    @SerializedName("IsInCorrectLocation") val isInCorrectLocation: Int,
    @SerializedName("IsCorrectFace") val isCorrectFace: Int,
    @SerializedName("IsVerified") val isVerified: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)
