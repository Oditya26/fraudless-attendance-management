package com.example.herenow.data.remote.sessions

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SessionsByDateApi {
    @GET("api/sessions/by-date")
    suspend fun byDate(@Query("date") date: String): Response<SessionsByDateResponse>
}
