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
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.snackbar.Snackbar
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

        // Logout
        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                showLogoutLoading(true)
                when (val result = repo.logout()) {
                    is LogoutResult.Success -> {
                        tokenManager.clear()
                        showLogoutLoading(false)
                        goToLogin()
                    }
                    is LogoutResult.Unauthorized -> {
                        tokenManager.clear()
                        showLogoutLoading(false)
                        goToLogin()
                    }
                    is LogoutResult.Failure -> {
                        showLogoutLoading(false)
                        // Pesan generik agar tidak membocorkan host/IP
                        Snackbar.make(binding.root, "Gagal logout. Coba lagi.", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Muat profil -> skeleton dulu
        showProfileSkeleton(true)
        loadProfile()
    }

    private fun loadProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!repo.hasToken()) {
                goToLogin(); return@launch
            }

            // Skeleton ON saat fetch
            showProfileSkeleton(true)

            when (val result = repo.fetchMe()) {
                is MeResult.Success -> {
                    val me = result.data
                    binding.tvName.text = me.StudentFullName
                    binding.tvId.text = me.StudentId
                    binding.tvEmail.text = me.Email

                    // Tampilkan konten, matikan skeleton
                    showProfileSkeleton(false)
                }
                is MeResult.Unauthorized -> {
                    tokenManager.clear()
                    // Tetap skeleton (jangan tampilkan default/error), lalu ke login
                    showProfileSkeleton(true)
                    goToLogin()
                }
                is MeResult.Failure -> {
                    // Tetap skeleton (tidak mengisi teks error atau default)
                    showProfileSkeleton(true)
                }
            }
        }
    }

    /** Skeleton untuk semua field profil (avatar, nama, id, email) */
    private fun showProfileSkeleton(show: Boolean) {
        val shName   = binding.shimmerName as? ShimmerFrameLayout
        val shId     = binding.shimmerId as? ShimmerFrameLayout
        val shEmail  = binding.shimmerEmail as? ShimmerFrameLayout

        if (show) {
            // Skeleton ON
            binding.tvName.visibility = View.GONE
            binding.tvId.visibility = View.GONE
            binding.tvEmail.visibility = View.GONE

            shName?.visibility   = View.VISIBLE; shName?.startShimmer()
            shId?.visibility     = View.VISIBLE; shId?.startShimmer()
            shEmail?.visibility  = View.VISIBLE; shEmail?.startShimmer()
        } else {
            // Skeleton OFF, tampilkan konten
            shName?.stopShimmer();   shName?.visibility   = View.GONE
            shId?.stopShimmer();     shId?.visibility     = View.GONE
            shEmail?.stopShimmer();  shEmail?.visibility  = View.GONE

            binding.tvName.visibility   = View.VISIBLE
            binding.tvId.visibility     = View.VISIBLE
            binding.tvEmail.visibility  = View.VISIBLE
        }
    }

    /** Spinner khusus proses logout (tidak untuk load profil) */
    private fun showLogoutLoading(show: Boolean) {
        binding.progressBar?.visibility = if (show) View.VISIBLE else View.GONE
        // Tidak menyembunyikan contentGroup agar skeleton tetap terlihat jika sedang tampil
        binding.btnLogout.isEnabled = !show
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
