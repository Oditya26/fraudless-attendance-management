package com.example.herenow.data.remote.room

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class RoomResponse(
    val RoomId: String?,
    val RoomCode: String?
)

interface RoomApi {
    @GET("/api/room")
    suspend fun getRoomById(
        @Header("Authorization") bearer: String,
        @Query("roomid") roomId: String
    ): Response<RoomResponse>
}
