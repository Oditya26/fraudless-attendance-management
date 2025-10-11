package com.example.herenow.data

import android.content.Context
import com.example.herenow.data.local.TokenManager
import com.example.herenow.data.remote.core.RetrofitProvider
import com.example.herenow.data.remote.profile.ProfileMe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

// ===== Result types =====
sealed class MeResult {
    data class Success(val data: ProfileMe) : MeResult()
    data object Unauthorized : MeResult()
    data class Failure(val message: String) : MeResult()
}

sealed class LogoutResult {
    data class Success(val message: String) : LogoutResult()
    data object Unauthorized : LogoutResult()
    data class Failure(val message: String) : LogoutResult()
}

class ProfileRepository(private val ctx: Context) {
    private val api = RetrofitProvider.provideProfileApi(ctx)
    private val tokenManager = TokenManager(ctx)

    suspend fun hasToken(): Boolean =
        !tokenManager.getToken().isNullOrBlank()

    suspend fun clearToken() {
        tokenManager.clear()
    }

    // GET /api/me
    suspend fun fetchMe(): MeResult = withContext(Dispatchers.IO) {
        try {
            val resp = api.me()
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body != null) MeResult.Success(body)
                else MeResult.Failure("Profil kosong")
            } else {
                if (resp.code() == 401) MeResult.Unauthorized
                else MeResult.Failure("Error ${resp.code()}: ${err(resp.errorBody()?.string())}")
            }
        } catch (e: HttpException) {
            MeResult.Failure("HTTP ${e.code()}")
        } catch (e: IOException) {
            MeResult.Failure("Tidak dapat terhubung ke server")
        } catch (e: Exception) {
            MeResult.Failure(e.message ?: "Error tidak diketahui")
        }
    }

    // POST /api/logout (Authorization: Bearer <token>)
    suspend fun logout(): LogoutResult = withContext(Dispatchers.IO) {
        try {
            val resp = api.logout()
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body != null) LogoutResult.Success(body.message)
                else LogoutResult.Failure("Respon kosong")
            } else {
                if (resp.code() == 401) LogoutResult.Unauthorized
                else LogoutResult.Failure("Error ${resp.code()}: ${err(resp.errorBody()?.string())}")
            }
        } catch (e: HttpException) {
            LogoutResult.Failure("HTTP ${e.code()}")
        } catch (e: IOException) {
            LogoutResult.Failure("Tidak bisa terhubung server")
        } catch (e: Exception) {
            LogoutResult.Failure(e.message ?: "Error tidak diketahui")
        }
    }

    private fun err(raw: String?): String = raw?.takeIf { it.isNotBlank() } ?: "Unknown"
}
