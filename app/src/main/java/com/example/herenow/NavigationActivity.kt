package com.example.herenow

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.example.herenow.databinding.ActivityNavigationBinding
import com.qamar.curvedbottomnaviagtion.CurvedBottomNavigation

class NavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNavigationBinding
    private var currentItemId: Int = R.id.bottom_home

    companion object {
        private const val STATE_SELECTED_ITEM = "state_selected_bottom_item"

        val HOME_ITEM = R.id.bottom_home
        val SCHEDULE_ITEM = R.id.bottom_schedule
        val ENROLLMENT_ITEM = R.id.bottom_enrollment
        val PROFILE_ITEM = R.id.bottom_profile

        // Urutan logis halaman (kiri ke kanan)
        private val NAV_ORDER = listOf(HOME_ITEM, SCHEDULE_ITEM, ENROLLMENT_ITEM, PROFILE_ITEM)
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

        currentItemId = savedInstanceState?.getInt(STATE_SELECTED_ITEM) ?: HOME_ITEM
        setupCurvedNavigation()

        if (savedInstanceState == null) {
            replaceRoot(HomeFragment(), isInitial = true)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fm = supportFragmentManager
                val current = fm.findFragmentById(R.id.nav_host_fragment)
                if (current is DetailFragment || fm.backStackEntryCount > 0) {
                    fm.popBackStack()
                } else {
                    showExitConfirm()
                }
            }
        })
    }

    private fun setupCurvedNavigation() {
        val nav = binding.curvedBottomNavigation

        val navItems = listOf(
            CurvedBottomNavigation.Model(HOME_ITEM, "Home", R.drawable.ic_home),
            CurvedBottomNavigation.Model(SCHEDULE_ITEM, "Schedule", R.drawable.ic_schedule),
            CurvedBottomNavigation.Model(ENROLLMENT_ITEM, "Enrollment", R.drawable.ic_enrollment),
            CurvedBottomNavigation.Model(PROFILE_ITEM, "Profile", R.drawable.ic_profile)
        )

        navItems.forEach { nav.add(it) }
        nav.show(currentItemId)

        nav.setOnClickMenuListener { item ->
            if (item.id == currentItemId) return@setOnClickMenuListener

            val fragment = when (item.id) {
                HOME_ITEM -> HomeFragment()
                SCHEDULE_ITEM -> ScheduleFragment()
                ENROLLMENT_ITEM -> EnrollmentFragment()
                PROFILE_ITEM -> ProfileFragment()
                else -> return@setOnClickMenuListener
            }

            replaceRoot(fragment, false, newItemId = item.id)
            currentItemId = item.id
        }

        nav.setOnReselectListener {
            // bisa digunakan untuk refresh current fragment
        }
    }

    private fun replaceRoot(fragment: Fragment, isInitial: Boolean, newItemId: Int = currentItemId) {
        if (isInitial) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.nav_host_fragment, fragment)
            }
            return
        }

        // Tentukan arah animasi berdasarkan urutan index
        val currentIndex = NAV_ORDER.indexOf(currentItemId)
        val newIndex = NAV_ORDER.indexOf(newItemId)

        val (enterAnim, exitAnim) = if (newIndex > currentIndex) {
            // Geser ke kanan (slide ke kiri)
            R.anim.slide_in_right to R.anim.slide_out_left
        } else {
            // Geser ke kiri (slide ke kanan)
            R.anim.slide_in_left to R.anim.slide_out_right
        }

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            setCustomAnimations(enterAnim, exitAnim)
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

        val accentColor = getColor(R.color.rich_electric_blue)
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        positiveButton.setTextColor(accentColor)
        negativeButton.setTextColor(accentColor)
        positiveButton.setTextAppearance(R.style.DescriptionBoldTextStyle)
        negativeButton.setTextAppearance(R.style.DescriptionBoldTextStyle)

        val parent = positiveButton.parent as LinearLayout
        parent.gravity = Gravity.CENTER
        parent.setPadding(8, 8, 8, 8)
    }
}
