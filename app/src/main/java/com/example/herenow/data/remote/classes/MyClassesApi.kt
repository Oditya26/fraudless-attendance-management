// com/example/herenow/data/remote/classes/MyClassesApi.kt
package com.example.herenow.data.remote.classes

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface MyClassesApi {
    @GET("api/my-classes")
    suspend fun myClasses(
        @Header("Authorization") auth: String
    ): Response<MyClassesResponse>
}
