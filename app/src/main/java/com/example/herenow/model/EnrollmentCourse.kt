package com.example.herenow.model

data class EnrollmentCourse(
    val classId: Int,                // ← dari "ClassId"
    val lecturerId: String,          // ← dari "LecturerId"
    val courseCode: String,          // ← dari "CourseId"
    val classCode: String,           // ← dari "ClassCode"
    val classYear: Int,              // ← dari "ClassYear"
    val semesterNumber: Int,         // ← dari "Semester"
    val totalSessions: Int,          // ← dari "NumberOfSession"
    val courseCategory: String,      // ← dari "CourseCategory"
    val lecturerFullName: String,    // ← dari "LecturerFullName"
    val courseTitle: String,         // ← dari "CourseName"
    val credits: Int,                // ← dari "Credit"

    // untuk UI
    val room: String = "-",          // placeholder (room diketahui di detail)
    val classType: String = courseCategory,
    val instructor: String = "$lecturerId - $lecturerFullName",
    val semester: String = formatSemester(classYear, semesterNumber)
) {
    companion object {
        fun formatSemester(year: Int, semester: Int): String {
            val label = if (semester % 2 == 1) "Odd" else "Even"
            return "$label Semester, $year"
        }
    }
}
