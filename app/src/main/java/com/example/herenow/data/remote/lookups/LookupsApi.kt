package com.example.herenow.data.remote.lookups

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface LookupsApi {
    @GET("api/lookups/by-category")
    suspend fun getByCategory(
        @Header("Authorization") bearer: String,
        @Query("lookupcategoryname") categoryName: String
    ): List<LookupItemDto>
}

data class LookupItemDto(
    val lookupid: Int,
    val lookupcategoryid: Int,
    val lookupcode: String?,
    val lookupdescription: String?,
    val lookupvalue: String?,
    val created_at: String?,
    val updated_at: String?
)
