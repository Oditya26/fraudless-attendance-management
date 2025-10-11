package com.example.herenow.data.remote.classes

data class MyClassesResponse(
    val classes: List<ClassItem>
)

data class ClassItem(
    val ClassId: Int,
    val LecturerId: String,
    val CourseId: String,
    val ClassCode: String,
    val LecturerRoleId: Int,
    val ClassYear: Int,
    val Semester: Int,
    val NumberOfSession: Int,
    val CourseCategory: String,
    val created_at: String?,
    val updated_at: String?,
    val LecturerFullName: String,
    val CourseName: String?,
    val Credit: Int?
)
