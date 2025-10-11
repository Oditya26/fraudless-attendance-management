package com.example.herenow.data.remote.auth

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Long
)

data class ErrorResponse(
    val error: String? = null
)
data class CheckTokenResponse(val message: String? = null)

