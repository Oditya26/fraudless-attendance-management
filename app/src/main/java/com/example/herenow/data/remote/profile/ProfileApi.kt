package com.example.herenow.data.remote.profile

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST

interface ProfileApi {
    @GET("api/me")
    suspend fun me(): Response<ProfileMe>

    @POST("api/logout")
    suspend fun logout(): Response<LogoutResponse>

}
