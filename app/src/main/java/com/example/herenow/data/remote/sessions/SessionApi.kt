package com.example.herenow.data.remote.sessions

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SessionsApi {
    @GET("api/sessions/by-year-semester")
    suspend fun sessionsByYearSemester(
        @Query("year") year: Int,
        @Query("semester") semester: Int
    ): Response<SessionsResponse>
}
