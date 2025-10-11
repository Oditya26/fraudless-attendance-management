package com.example.herenow

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.herenow.data.LogoutResult
import com.example.herenow.data.MeResult
import com.example.herenow.data.ProfileRepository
import com.example.herenow.data.local.TokenManager
import com.example.herenow.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: ProfileRepository
    private lateinit var tokenManager: TokenManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = ProfileRepository(requireContext())
        tokenManager = TokenManager(requireContext())

        // tombol logout
        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                showLoading(true)
                when (val result = repo.logout()) {
                    is LogoutResult.Success -> {
                        // clear token lokal
                        tokenManager.clear()
                        showLoading(false)
                        goToLogin()
                    }
                    is LogoutResult.Unauthorized -> {
                        tokenManager.clear()
                        showLoading(false)
                        goToLogin()
                    }
                    is LogoutResult.Failure -> {
                        showLoading(false)
                        // misalnya tampilkan di TextView email
                        binding.tvEmail.text = result.message
                    }
                }
            }
        }


        // muat profil
        loadProfile()
    }

    private fun loadProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            // token check
            if (!repo.hasToken()) {
                goToLogin(); return@launch
            }

            showLoading(true)
            when (val result = repo.fetchMe()) {
                is MeResult.Success -> {
                    showLoading(false)
                    val me = result.data
                    binding.tvName.text = me.StudentFullName
                    binding.tvId.text = me.StudentId
                    binding.tvEmail.text = me.Email
                }
                is MeResult.Unauthorized -> {
                    showLoading(false)
                    tokenManager.clear()
                    goToLogin()
                }
                is MeResult.Failure -> {
                    showLoading(false)
                    binding.tvName.text = "Gagal memuat profil"
                    binding.tvId.text = "-"
                    binding.tvEmail.text = result.message
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar?.visibility = if (show) View.VISIBLE else View.GONE
        binding.contentGroup?.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun goToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
