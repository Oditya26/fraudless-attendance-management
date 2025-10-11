package com.example.herenow

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.example.herenow.databinding.ActivityNavigationBinding
import com.google.android.material.navigation.NavigationBarView

class NavigationActivity : AppCompatActivity(), NavigationBarView.OnItemSelectedListener {

    private lateinit var binding: ActivityNavigationBinding

    // Simpan tab terpilih sekarang (juga dipulihkan saat recreate)
    private var currentItemId: Int = R.id.bottom_home

    companion object {
        private const val STATE_SELECTED_ITEM = "state_selected_bottom_item"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Pulihkan tab terakhir jika ada
        currentItemId = savedInstanceState?.getInt(STATE_SELECTED_ITEM) ?: R.id.bottom_home
        binding.bottomNavigationView.setOnItemSelectedListener(this)

        // Hanya set fragment awal saat first launch
        if (savedInstanceState == null) {
            binding.bottomNavigationView.selectedItemId = currentItemId
            replaceRoot(HomeFragment(), isInitial = true)
        } else {
            // Sinkronkan tampilan tombol dengan state
            binding.bottomNavigationView.selectedItemId = currentItemId
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED_ITEM, currentItemId)
    }

    // JANGAN memaksa pindah ke Home di onResume()
    // override fun onResume() { ... }  --> DIHAPUS

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (item.itemId == currentItemId) return true

        val target: Fragment = when (item.itemId) {
            R.id.bottom_home       -> HomeFragment()
            R.id.bottom_schedule   -> ScheduleFragment()
            R.id.bottom_enrollment -> EnrollmentFragment()
            R.id.bottom_profile    -> ProfileFragment()
            else -> return false
        }

        // Hindari transaksi kalau fragment yang sama sudah tampil
        val current = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        if (current != null && current::class == target::class) {
            currentItemId = item.itemId
            return true
        }

        replaceRoot(target, isInitial = false)
        currentItemId = item.itemId
        return true
    }

    private fun replaceRoot(fragment: Fragment, isInitial: Boolean) {
        val (enterAnim, exitAnim) = when (currentItemId) {
            R.id.bottom_home -> R.anim.slide_in_right to R.anim.slide_out_left
            R.id.bottom_schedule -> {
                if (fragment is HomeFragment) R.anim.slide_in_left to R.anim.slide_out_right
                else R.anim.slide_in_right to R.anim.slide_out_left
            }
            R.id.bottom_enrollment -> {
                if (fragment is ProfileFragment) R.anim.slide_in_right to R.anim.slide_out_left
                else R.anim.slide_in_left to R.anim.slide_out_right
            }
            R.id.bottom_profile -> R.anim.slide_in_left to R.anim.slide_out_right
            else -> R.anim.slide_in_right to R.anim.slide_out_left
        }

        // Gunakan commit{} KTX; tidak melakukan apa-apa di onResume,
        // hanya saat pengguna memilih tab / first launch.
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            if (!isInitial) {
                setCustomAnimations(enterAnim, exitAnim)
            }
            replace(R.id.nav_host_fragment, fragment)
            // Tidak addToBackStack untuk bottom nav (pola umum)
        }
    }
}
