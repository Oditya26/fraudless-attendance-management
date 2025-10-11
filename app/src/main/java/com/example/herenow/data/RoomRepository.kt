package com.example.herenow.data

import android.content.Context
import com.example.herenow.data.remote.core.RetrofitProvider
import com.example.herenow.data.remote.room.RoomApi
import com.example.herenow.data.remote.room.RoomResponse
import com.example.herenow.data.local.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class RoomResult {
    data class Success(val data: RoomResponse): RoomResult()
    data class Unauthorized(val message: String): RoomResult()
    data class Failure(val message: String): RoomResult()
}

class RoomRepository(context: Context) {
    private val api = RetrofitProvider.provideRetrofit(context).create(RoomApi::class.java)
    private val tokenManager = TokenManager(context)

    suspend fun fetchRoom(roomId: String): RoomResult = withContext(Dispatchers.IO) {
        try {
            val token = tokenManager.getToken()
                ?: return@withContext RoomResult.Unauthorized("No token")
            val resp = api.getRoomById("Bearer $token", roomId)
            if (!resp.isSuccessful) {
                return@withContext when (resp.code()) {
                    401 -> RoomResult.Unauthorized("Unauthenticated")
                    else -> RoomResult.Failure("Error ${resp.code()}: ${resp.errorBody()?.string()}")
                }
            }
            val body = resp.body()
                ?: return@withContext RoomResult.Failure("Empty response")
            RoomResult.Success(body)
        } catch (e: Exception) {
            RoomResult.Failure(e.message ?: "Unknown error")
        }
    }
}
