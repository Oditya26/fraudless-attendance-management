package com.example.herenow

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.example.herenow.databinding.ActivityNavigationBinding
import com.google.android.material.navigation.NavigationBarView
import androidx.activity.addCallback

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

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val fm = supportFragmentManager
                    val current = fm.findFragmentById(R.id.nav_host_fragment)

                    if (current is DetailFragment || fm.backStackEntryCount > 0) {
                        // Hanya DetailFragment (atau ada tumpukan) yang boleh back
                        fm.popBackStack()
                    } else {
                        // Selain itu, tawarkan keluar aplikasi
                        showExitConfirm()
                    }
                }
            }
        )
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

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            if (!isInitial) {
                setCustomAnimations(enterAnim, exitAnim)
            }

            // 🔥 Kosongkan semua backstack sebelumnya agar antar-tab tidak nyampur
            supportFragmentManager.popBackStack(
                null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
            )

            replace(R.id.nav_host_fragment, fragment)
            // Tidak addToBackStack untuk bottom nav
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun showExitConfirm() {
        val builder = AlertDialog.Builder(this, R.style.RoundedAlertDialog)

        // 🔹 Container agar ada margin ke tepi layar
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8) // margin ke layar
            gravity = Gravity.CENTER
        }

        // 🔹 Text Message
        val messageView = TextView(this).apply {
            text = getString(R.string.exit_message)
            setTextAppearance(R.style.DescriptionBoldTextStyle)
            setPadding(8, 8, 8, 8)
        }

        container.addView(messageView)
        builder.setView(container)

        builder.setPositiveButton(getString(R.string.exit_yes)) { _, _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
            } else {
                @Suppress("DEPRECATION")
                finishAffinity()
            }
        }

        builder.setNegativeButton(getString(R.string.exit_no), null)
        builder.setCancelable(true)

        val dialog = builder.create()
        dialog.show()

        // 🔹 Rounded background
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)

        // 🔹 Tombol ke tengah
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val accentColor = getColor(R.color.rich_electric_blue)

        positiveButton.setTextColor(accentColor)
        negativeButton.setTextColor(accentColor)
        positiveButton.setTextAppearance(R.style.DescriptionBoldTextStyle)
        negativeButton.setTextAppearance(R.style.DescriptionBoldTextStyle)

        // 🔹 Pusatkan tombol
        val parent = positiveButton.parent as LinearLayout
        parent.gravity = Gravity.CENTER
        parent.setPadding(8, 8, 8, 8)
    }


}
