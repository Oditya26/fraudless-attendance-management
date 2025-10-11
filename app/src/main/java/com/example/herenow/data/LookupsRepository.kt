package com.example.herenow.data

import android.content.Context
import com.example.herenow.data.local.TokenManager
import com.example.herenow.data.remote.lookups.LookupsApi
import com.example.herenow.data.remote.lookups.LookupItemDto
import retrofit2.HttpException
import retrofit2.Retrofit

sealed class LookupsResult {
    data class Success(val items: List<LookupItemDto>) : LookupsResult()
    data class Unauthorized(val message: String) : LookupsResult()
    data class Failure(val message: String) : LookupsResult()
}

class LookupsRepository(context: Context) {

    private val tokenManager: TokenManager = TokenManager(context)
    private val retrofit: Retrofit = ApiClient.provideRetrofit(context)
    private val api: LookupsApi = retrofit.create(LookupsApi::class.java)

    /** Fungsi utama: ambil lookup by category name */
    suspend fun fetchByCategory(categoryName: String): LookupsResult {
        return try {
            val token = tokenManager.getToken() ?: return LookupsResult.Unauthorized("No token")
            // Catatan: sesuaikan tipe return LookupsApi.getByCategory(...)
            // Jika API mengembalikan Response<List<LookupItemDto>>, gunakan .body()!!
            val items: List<LookupItemDto> = api.getByCategory("Bearer $token", categoryName)
            LookupsResult.Success(items)
        } catch (e: HttpException) {
            if (e.code() == 401) LookupsResult.Unauthorized("Unauthenticated")
            else LookupsResult.Failure(e.message())
        } catch (e: Exception) {
            LookupsResult.Failure(e.message ?: "Unknown error")
        }
    }

    /** Alias untuk kompatibilitas dengan pemanggilan lama */
    suspend fun getByCategory(categoryName: String): LookupsResult = fetchByCategory(categoryName)
}
