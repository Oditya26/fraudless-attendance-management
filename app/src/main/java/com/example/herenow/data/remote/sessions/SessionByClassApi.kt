package com.example.herenow.data.remote.sessions

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface SessionsByClassApi {
    @GET("api/sessions/class/{classId}")
    suspend fun byClass(@Path("classId") classId: Int): Response<SessionsByClassResponse>
}
