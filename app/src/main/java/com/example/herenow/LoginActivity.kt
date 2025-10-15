package com.example.herenow

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.herenow.data.AuthRepository
import com.example.herenow.data.LoginResult
import com.example.herenow.data.TokenCheckResult
import com.example.herenow.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepository = AuthRepository.create(this)

        // ===== Cek token saat pertama kali masuk =====
        lifecycleScope.launch {
            val token = authRepository.getToken()
            if (!token.isNullOrBlank()) {
                when (val result = authRepository.checkToken()) {
                    is TokenCheckResult.Authorized -> {
                        // Token masih valid → langsung masuk ke NavigationActivity
                        goMain()
                        return@launch
                    }
                    is TokenCheckResult.Unauthorized -> {
                        // Token invalid/expired → hapus token, biarkan user login
                        authRepository.clearToken()
                    }
                    is TokenCheckResult.Failure -> {
                        // Kalau gagal koneksi, bisa arahkan ke login saja
                        // (atau tergantung kebutuhan, boleh juga coba masuk offline mode)
                    }
                }
            }
        }

        // ===== Tombol login normal =====
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                showCustomToast("Email and Password cannot be empty.")
                return@setOnClickListener
            }

            // Tombol berubah seperti ditekan
            binding.btnLogin.apply {
                isEnabled = false
                alpha = 0.6f // efek ditekan
                text = "Processing..."
            }

            // Tampilkan loading
            binding.progressBarLogin.visibility = android.view.View.VISIBLE

            lifecycleScope.launch {
                when (val result = authRepository.login(email, password)) {
                    is LoginResult.Success -> {
                        showCustomToast("Login successful")
                        goMain()
                    }
                    is LoginResult.Failure -> {
                        showCustomToast(result.message)
                        // Kembalikan tombol ke keadaan semula
                        binding.btnLogin.apply {
                            isEnabled = true
                            alpha = 1f
                            text = "Login"
                        }
                        binding.progressBarLogin.visibility = android.view.View.GONE
                    }
                }
            }
        }

    }

    private fun goMain() {
        startActivity(Intent(this, NavigationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }

    private fun showCustomToast(message: String) {
        val layout = layoutInflater.inflate(R.layout.custom_toast, null)
        val textView = layout.findViewById<TextView>(R.id.tvMessage)
        textView.text = message
        Toast(applicationContext).apply {
            duration = Toast.LENGTH_SHORT
            view = layout
        }.show()
    }
}
