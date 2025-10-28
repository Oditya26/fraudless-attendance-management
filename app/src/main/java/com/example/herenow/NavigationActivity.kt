package com.example.herenow

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.example.herenow.databinding.ActivityNavigationBinding

class NavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNavigationBinding
    private var currentItemId: Int = R.id.bottom_home
    private var lastSelectedIndex = 0
    private var currentFragmentTag: String? = null

    companion object {
        val HOME_ITEM = R.id.nav_home
        val SCHEDULE_ITEM = R.id.nav_schedule
        val ENROLLMENT_ITEM = R.id.nav_enrollment
        val PROFILE_ITEM = R.id.nav_profile
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupBottomNavigation()
        replaceRoot(HomeFragment(), isInitial = true)
        currentFragmentTag = HomeFragment::class.java.simpleName

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fm = supportFragmentManager
                if (fm.backStackEntryCount > 0) fm.popBackStack()
                else showExitConfirm()
            }
        })
    }

    private fun setupBottomNavigation() {
        val items = listOf(
            binding.bottomNavBar.findViewById<LinearLayout>(R.id.nav_home),
            binding.bottomNavBar.findViewById(R.id.nav_schedule),
            binding.bottomNavBar.findViewById(R.id.nav_enrollment),
            binding.bottomNavBar.findViewById(R.id.nav_profile)
        )

        items.forEach { item ->
            item.setOnClickListener {
                when (item.id) {
                    R.id.nav_home -> selectItem(item, HomeFragment())
                    R.id.nav_schedule -> selectItem(item, ScheduleFragment())
                    R.id.nav_enrollment -> selectItem(item, EnrollmentFragment())
                    R.id.nav_profile -> selectItem(item, ProfileFragment())
                }
            }
        }

        highlightSelected(R.id.nav_home)
    }

    private fun selectItem(item: LinearLayout, fragment: Fragment) {
        val index = when (item.id) {
            R.id.nav_home -> 0
            R.id.nav_schedule -> 1
            R.id.nav_enrollment -> 2
            R.id.nav_profile -> 3
            else -> 0
        }

        // Kalau user klik tab yang sama, abaikan (tidak ganti fragment & tidak animasi)
        val newTag = fragment::class.java.simpleName
        if (currentFragmentTag == newTag) return

        // Tentukan arah animasi berdasarkan urutan tombol yang diklik
        val (enterAnim, exitAnim) = if (index > lastSelectedIndex) {
            R.anim.slide_in_right to R.anim.slide_out_left
        } else {
            R.anim.slide_in_left to R.anim.slide_out_right
        }

        lastSelectedIndex = index
        highlightSelected(item.id)

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            setCustomAnimations(enterAnim, exitAnim)
            replace(R.id.nav_host_fragment, fragment, newTag)
        }

        currentFragmentTag = newTag
    }

    private fun highlightSelected(selectedId: Int) {
        val items = listOf(
            R.id.nav_home, R.id.nav_schedule, R.id.nav_enrollment, R.id.nav_profile
        )

        for (id in items) {
            val icon = findViewById<ImageView>(
                when (id) {
                    R.id.nav_home -> R.id.icon_home
                    R.id.nav_schedule -> R.id.icon_schedule
                    R.id.nav_enrollment -> R.id.icon_enrollment
                    else -> R.id.icon_profile
                }
            )
            val text = findViewById<TextView>(
                when (id) {
                    R.id.nav_home -> R.id.text_home
                    R.id.nav_schedule -> R.id.text_schedule
                    R.id.nav_enrollment -> R.id.text_enrollment
                    else -> R.id.text_profile
                }
            )

            val color = if (id == selectedId)
                ContextCompat.getColor(this, R.color.white)
            else
                ContextCompat.getColor(this, R.color.ateneo_blue)

            icon.setColorFilter(color)
            text.setTextColor(color)
        }
    }

    private fun replaceRoot(fragment: Fragment, isInitial: Boolean = false) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.nav_host_fragment, fragment)
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun showExitConfirm() {
        val builder = AlertDialog.Builder(this, R.style.RoundedAlertDialog)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER
        }
        val messageView = TextView(this).apply {
            text = getString(R.string.exit_message)
            setTextAppearance(R.style.DescriptionBoldTextStyle)
            setPadding(8, 8, 8, 8)
        }
        container.addView(messageView)
        builder.setView(container)
        builder.setPositiveButton(getString(R.string.exit_yes)) { _, _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) finishAndRemoveTask()
            else finishAffinity()
        }
        builder.setNegativeButton(getString(R.string.exit_no), null)
        builder.setCancelable(true)

        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
    }
}
