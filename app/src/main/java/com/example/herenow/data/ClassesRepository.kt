package com.example.herenow.data

import android.content.Context
import com.example.herenow.data.local.TokenManager
import com.example.herenow.data.remote.classes.MyClassesApi
import com.example.herenow.data.remote.classes.MyClassesResponse
import com.example.herenow.model.EnrollmentCourse
import com.example.herenow.data.remote.core.RetrofitProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

sealed class MyClassesResult {
    data class Success(val enrollments: List<EnrollmentCourse>) : MyClassesResult()
    data class Unauthorized(val message: String) : MyClassesResult()
    data class Failure(val message: String) : MyClassesResult()
}

class ClassesRepository(private val context: Context) {

    private val tokenManager = TokenManager(context)
    private val api: MyClassesApi =
        RetrofitProvider.provideRetrofit(context).create(MyClassesApi::class.java)

    suspend fun fetchMyEnrollments(): MyClassesResult = withContext(Dispatchers.IO) {
        try {
            val token = tokenManager.getToken()
                ?: return@withContext MyClassesResult.Unauthorized("No token")

            val resp = api.myClasses("Bearer $token")
            if (!resp.isSuccessful) {
                return@withContext when (resp.code()) {
                    401 -> MyClassesResult.Unauthorized("Unauthenticated")
                    else -> MyClassesResult.Failure("Error ${resp.code()}: ${resp.errorBody()?.string()}")
                }
            }

            val body: MyClassesResponse = resp.body() as MyClassesResponse?
                ?: return@withContext MyClassesResult.Failure("Empty response")

            val mapped = body.classes.map { c ->
                // Label semester: Odd untuk 1,3,5,... ; Even untuk 2,4,6,...
                val semLabel = if (c.Semester % 2 == 1) "Odd" else "Even"
                val semesterText = "$semLabel Semester, ${c.ClassYear}"

                EnrollmentCourse(
                    // field untuk kartu
                    semester       = semesterText,
                    room           = c.ClassCode,                             // tampil di txtRoomCode
                    courseCode     = c.CourseId,                              // tampil di txtCourseCode
                    courseTitle    = c.CourseName ?: c.CourseId,              // tampil di txtCourseTitle
                    classType      = c.CourseCategory,                        // tampil di txtClassType
                    instructor     = "${c.LecturerId} - ${c.LecturerFullName}", // tampil di txtInstructor
                    credits        = c.Credit ?: 0,                           // tampil di txtCredits
                    totalSessions  = c.NumberOfSession,

                    // metadata tambahan (kalau model kamu dukung)
                    classId        = c.ClassId,
                    lecturerId     = c.LecturerId,
                    classCode      = c.ClassCode,
                    classYear      = c.ClassYear,
                    semesterNumber = c.Semester,
                    lecturerFullName = c.LecturerFullName,
                    courseCategory = c.CourseCategory
                )
            }

            MyClassesResult.Success(mapped)

        } catch (e: HttpException) {
            if (e.code() == 401) MyClassesResult.Unauthorized("Unauthenticated")
            else MyClassesResult.Failure("HTTP ${e.code()}: ${e.message()}")
        } catch (e: Exception) {
            MyClassesResult.Failure(e.message ?: "Unknown error")
        }
    }
}
