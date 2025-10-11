package com.example.herenow.data.remote.sessionsbydate

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface ByDateApi {
    @GET("api/sessions/by-date")
    suspend fun getByDate(
        @Header("Authorization") bearer: String,
        @Query("date") date: String // "YYYY-MM-DD"
    ): ByDateResponse
}
