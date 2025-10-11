package com.example.herenow.data

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.herenow.data.home.HomeNowSession
import com.example.herenow.data.local.TokenManager
import com.example.herenow.data.remote.sessionsbydate.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.time.LocalDate
import java.time.LocalTime

sealed class TodayNowResult {
    data class Success(val session: HomeNowSession): TodayNowResult()
    object Empty: TodayNowResult()
    data class Unauthorized(val message: String): TodayNowResult()
    data class Failure(val message: String): TodayNowResult()
}

class SessionsByDateRepository(ctx: Context) {
    private val retrofit = ApiClient.provideRetrofit(ctx)
    private val api = retrofit.create(ByDateApi::class.java)
    private val tokenManager = TokenManager(ctx)

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun fetchNow(date: String): TodayNowResult = withContext(Dispatchers.IO) {
        try {
            val token = tokenManager.getToken() ?: return@withContext TodayNowResult.Unauthorized("No token")
            val resp = api.getByDate("Bearer $token", date)
            if (resp.data.isEmpty()) return@withContext TodayNowResult.Empty

            val now = LocalTime.now()
            val items = resp.data

            // helper pilih attended?
            fun isAttended(p: List<ByDatePresenceDto>) =
                p.any { it.isInCorrectLocation == 1 && it.isCorrectFace == 1 && it.isVerified == 1 }

            // parse time safely
            fun toLocalTime(hms: String): LocalTime =
                runCatching { LocalTime.parse(hms) }.getOrDefault(LocalTime.MIN)

            // 1) Ongoing (now in [start, end))
            val ongoing = items.firstOrNull { it ->
                val st = toLocalTime(it.session.shiftStart)
                val en = toLocalTime(it.session.shiftEnd)
                !now.isBefore(st) && now.isBefore(en)
            }

            val chosen = ongoing ?: run {
                // 2) Upcoming: min by start > now
                val upcoming = items
                    .filter { now.isBefore(toLocalTime(it.session.shiftStart)) }
                    .minByOrNull { toLocalTime(it.session.shiftStart) }

                upcoming ?: items.maxByOrNull { toLocalTime(it.session.shiftEnd) } // 3) latest past
            }

            if (chosen == null) return@withContext TodayNowResult.Empty

            val c = chosen.clazz
            val s = chosen.session
            val model = HomeNowSession(
                classId = c.classId,
                classCode = c.classCode,
                courseName = c.courseName,
                courseCategory = c.courseCategory,
                lecturerFullName = c.lecturerFullName,
                credit = c.credit,
                roomId = s.roomId,
                sessionDate = s.sessionDate,
                sessionNumber = s.sessionNumber,
                shift = s.shift,
                shiftStart = s.shiftStart,
                shiftEnd = s.shiftEnd,
                attended = isAttended(chosen.presences)
            )
            TodayNowResult.Success(model)
        } catch (e: HttpException) {
            if (e.code() == 401) TodayNowResult.Unauthorized("Unauthenticated")
            else TodayNowResult.Failure(e.message())
        } catch (e: Exception) {
            TodayNowResult.Failure(e.message ?: "Unknown error")
        }
    }
}
