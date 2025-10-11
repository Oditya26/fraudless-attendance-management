package com.example.herenow.data

import android.content.Context
import com.example.herenow.data.local.TokenManager
import com.example.herenow.data.remote.auth.AuthApi
import com.example.herenow.data.remote.auth.LoginRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

sealed class TokenCheckResult {
    data object Authorized : TokenCheckResult()
    data object Unauthorized : TokenCheckResult()
    data class Failure(val message: String) : TokenCheckResult()
}

sealed class LoginResult {
    data object Success : LoginResult()
    data class Failure(val message: String) : LoginResult()
}

class AuthRepository(
    private val api: AuthApi,
    private val tokenManager: TokenManager
) {
    companion object {
        fun create(context: Context): AuthRepository {
            val api = com.example.herenow.data.remote.core.RetrofitProvider.provideAuthApi(context)
            val token = TokenManager(context)
            return AuthRepository(api, token)
        }
    }

    suspend fun getToken(): String? = tokenManager.getToken()
    suspend fun clearToken() = tokenManager.clear()

    suspend fun login(email: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val resp = api.login(LoginRequest(email, password))
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body != null && !body.access_token.isNullOrBlank()) {
                    tokenManager.saveToken(body.access_token)
                    LoginResult.Success
                } else {
                    LoginResult.Failure("Login gagal: token kosong")
                }
            } else {
                if (resp.code() == 401) {
                    LoginResult.Failure("Unauthorized: Email/Password salah")
                } else {
                    LoginResult.Failure("Error ${resp.code()}: ${resp.errorBody()?.string() ?: "Unknown"}")
                }
            }
        } catch (e: HttpException) {
            LoginResult.Failure("HTTP Error: ${e.code()}")
        } catch (e: IOException) {
            LoginResult.Failure("Tidak dapat terhubung ke server")
        } catch (e: Exception) {
            LoginResult.Failure(e.message ?: "Error tidak diketahui")
        }
    }

    suspend fun logout() {
        tokenManager.clear()
    }

    suspend fun checkToken(): TokenCheckResult = withContext(Dispatchers.IO) {
        try {
            val resp = api.checkToken()
            if (resp.isSuccessful) {
                TokenCheckResult.Authorized
            } else {
                if (resp.code() == 401) TokenCheckResult.Unauthorized
                else TokenCheckResult.Failure("Error ${resp.code()}: ${resp.errorBody()?.string() ?: "Unknown"}")
            }
        } catch (e: HttpException) {
            TokenCheckResult.Failure("HTTP ${e.code()}")
        } catch (e: IOException) {
            TokenCheckResult.Failure("Tidak dapat terhubung ke server")
        } catch (e: Exception) {
            TokenCheckResult.Failure(e.message ?: "Error tidak diketahui")
        }
    }

    // Untuk API lain: panggil RetrofitProvider yang sama, header Authorization akan otomatis ikut.
}
