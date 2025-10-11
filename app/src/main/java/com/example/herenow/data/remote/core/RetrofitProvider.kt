package com.example.herenow.data.remote.core

import android.content.Context
import com.example.herenow.data.local.TokenManager
import com.example.herenow.data.remote.auth.AuthApi
import com.example.herenow.data.remote.sessions.SessionsByClassApi
import com.example.herenow.data.remote.sessions.SessionsByDateApi
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitProvider {
    private const val BASE_URL = "http://202.10.44.214:8000/"

    fun provideRetrofit(context: Context): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(provideOkHttp(context))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun provideOkHttp(context: Context): OkHttpClient {
        val tokenManager = TokenManager(context)

        val authInterceptor = Interceptor { chain ->
            val token = runBlocking { tokenManager.getToken() } // ⬅️ menunggu hasil suspend secara sinkron
            val req: Request = if (!token.isNullOrEmpty()) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else chain.request()
            chain.proceed(req)
        }


        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    fun provideAuthApi(context: Context): AuthApi {
        val tokenManager = TokenManager(context)
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(AuthInterceptor(tokenManager)) // otomatis bawa Bearer (untuk API lain)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }

    fun provideProfileApi(context: Context): com.example.herenow.data.remote.profile.ProfileApi {
        val tokenManager = com.example.herenow.data.local.TokenManager(context)
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(AuthInterceptor(tokenManager))
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.example.herenow.data.remote.profile.ProfileApi::class.java)
    }

    fun provideMyClassesApi(context: Context): com.example.herenow.data.remote.classes.MyClassesApi {
        val tokenManager = com.example.herenow.data.local.TokenManager(context)
        val logging = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(AuthInterceptor(tokenManager))
            .build()

        return retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(com.example.herenow.data.remote.classes.MyClassesApi::class.java)
    }

    fun provideSessionsApi(context: Context): com.example.herenow.data.remote.sessions.SessionsApi {
        val tokenManager = com.example.herenow.data.local.TokenManager(context)
        val logging = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(AuthInterceptor(tokenManager))
            .build()

        return retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(com.example.herenow.data.remote.sessions.SessionsApi::class.java)
    }

    fun provideSessionsByDateApi(ctx: Context): SessionsByDateApi {
        return provideRetrofit(ctx).create(SessionsByDateApi::class.java)
    }

    fun provideSessionsByClassApi(context: Context): SessionsByClassApi =
        provideRetrofit(context).create(SessionsByClassApi::class.java)

}
