package com.example.herenow.data

import android.content.Context
import com.example.herenow.data.remote.core.RetrofitProvider
import com.example.herenow.data.remote.sessions.*
import com.example.herenow.model.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

sealed class SessionsResult {
    data class Success(
        val schedules: List<Schedule>,
        val courseTitleByCode: Map<String, String>
    ) : SessionsResult()
    data object Unauthorized : SessionsResult()
    data class Failure(val message: String) : SessionsResult()
}

class SessionsRepository(ctx: Context) {
    private val api = RetrofitProvider.provideSessionsApi(ctx)

    suspend fun fetch(year: Int, semester: Int): SessionsResult = withContext(Dispatchers.IO) {
        try {
            val resp = api.sessionsByYearSemester(year, semester)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return@withContext SessionsResult.Success(emptyList(), emptyMap())

                val titles = mutableMapOf<String, String>()
                val out = mutableListOf<Schedule>()

                body.data.forEach { cw ->
                    val c = cw.`class`
                    titles[c.CourseId] = c.CourseName

                    cw.sessions.forEach { s ->
                        val date = LocalDate.parse(s.SessionDate) // "YYYY-MM-DD"
                        val time = "${s.shift_start.substring(0,5)} - ${s.shift_end.substring(0,5)} WIB"
                        out += Schedule(
                            date = date,
                            room = s.RoomId.ifBlank { c.ClassCode },
                            courseCode = c.CourseId,
                            time = time,
                            session = s.SessionNumber,
                            classId = c.ClassId              // ← tambahkan ini
                        )
                    }
                }

                SessionsResult.Success(out, titles)
            } else {
                if (resp.code() == 401) SessionsResult.Unauthorized
                else SessionsResult.Failure("Error ${resp.code()}: ${resp.errorBody()?.string() ?: "Unknown"}")
            }
        } catch (e: HttpException) {
            SessionsResult.Failure("HTTP ${e.code()}")
        } catch (e: IOException) {
            SessionsResult.Failure("Tidak dapat terhubung ke server")
        } catch (e: Exception) {
            SessionsResult.Failure(e.message ?: "Error tidak diketahui")
        }
    }
}
