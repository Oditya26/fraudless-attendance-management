package com.example.herenow.data.remote.auth

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body

interface AuthApi {
    @POST("api/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("api/auth/check-token")
    suspend fun checkToken(): Response<CheckTokenResponse>
}