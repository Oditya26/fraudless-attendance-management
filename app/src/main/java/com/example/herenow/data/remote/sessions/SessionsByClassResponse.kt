package com.example.herenow.data.remote.sessions

import com.google.gson.annotations.SerializedName

// ====== Response wrapper ======
data class SessionsByClassResponse(
    @SerializedName("class")    val clazz: ClassMetaDto,
    @SerializedName("sessions") val sessions: List<ClassSessionDto>
)

// ====== Class meta ======
data class ClassMetaDto(
    @SerializedName("ClassId")          val classId: Int,
    @SerializedName("LecturerId")       val lecturerId: String?,
    @SerializedName("CourseId")         val courseId: String?,
    @SerializedName("ClassCode")        val classCode: String?,
    @SerializedName("LecturerRoleId")   val lecturerRoleId: Int?,
    @SerializedName("ClassYear")        val classYear: Int?,
    @SerializedName("Semester")         val semester: Int?,
    @SerializedName("NumberOfSession")  val numberOfSession: Int?,
    @SerializedName("CourseCategory")   val courseCategory: String?,
    @SerializedName("LecturerFullName") val lecturerFullName: String?,
    @SerializedName("CourseName")       val courseName: String?,
    @SerializedName("Credit")           val credit: Int?
)

// ====== Session item ======
data class ClassSessionDto(
    @SerializedName("SessionId")        val sessionId: Int?,          // bisa tidak ada pada beberapa API
    @SerializedName("ClassId")          val classId: Int?,            // bisa tidak ada pada beberapa API
    @SerializedName("SessionDate")      val sessionDate: String,      // "YYYY-MM-DD"
    @SerializedName("RoomId")           val roomId: String?,          // nullable: aman utk DetailFragment.getRoomIdForSession
    @SerializedName("SessionNumber")    val sessionNumber: Int,
    @SerializedName("Shift")            val shift: Int?,              // optional
    @SerializedName(value = "ShiftStart", alternate = ["shift_start"])
    val shiftStart: String,                                           // "HH:mm:ss"
    @SerializedName(value = "ShiftEnd",   alternate = ["shift_end"])
    val shiftEnd: String,                                             // "HH:mm:ss"
    @SerializedName("presences")        val presences: List<PresenceDto>?
)

// ====== Presence (TOP-LEVEL, satu-satunya) ======
data class PresenceDto(
    @SerializedName("PresenceId")          val presenceId: Int,
    @SerializedName("SessionId")           val sessionId: Int?,       // optional di beberapa respon
    @SerializedName("StudentId")           val studentId: String?,    // optional di beberapa respon
    @SerializedName("IsInCorrectLocation") val isInCorrectLocation: Int?, // 1/0/null
    @SerializedName("IsCorrectFace")       val isCorrectFace: Int?,       // 1/0/null
    @SerializedName("IsVerified")          val isVerified: Int?           // 1/0/null
)
