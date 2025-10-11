package com.example.herenow

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.herenow.data.ProfileRepository
import com.example.herenow.data.SessionsByDateRepository
import com.example.herenow.data.TodayNowResult
import com.example.herenow.data.local.TokenManager
import com.example.herenow.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    // Tampilkan kartu mulai X menit sebelum jam mulai hingga sebelum jam selesai
    private val MINUTES_BEFORE_START = 15

    private lateinit var profileRepo: ProfileRepository
    private lateinit var sessionsRepo: SessionsByDateRepository
    private lateinit var tokenManager: TokenManager
    private val BASE_URL = "http://202.10.44.214:8000"

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    @RequiresApi(Build.VERSION_CODES.O)
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale("id", "ID"))

    // ---------- Flexible parsers ----------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseLocalDateFlexible(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        // ISO-8601 "YYYY-MM-DD"
        runCatching { return LocalDate.parse(raw.take(10)) }.getOrNull()?.let { return it }
        // LocalDateTime / OffsetDateTime
        runCatching { return LocalDateTime.parse(raw).toLocalDate() }.getOrNull()?.let { return it }
        runCatching { return OffsetDateTime.parse(raw).toLocalDate() }.getOrNull()?.let { return it }
        // Fallback: ambil potongan "YYYY-MM-DD"
        val m = Regex("""\d{4}-\d{2}-\d{2}""").find(raw) ?: return null
        return runCatching { LocalDate.parse(m.value) }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseLocalTimeFlexible(raw: String?): LocalTime? {
        if (raw.isNullOrBlank()) return null
        // Terima "HH:mm:ss" atau "HH:mm"
        return runCatching { LocalTime.parse(raw) }.getOrNull()
            ?: runCatching { LocalTime.parse(raw.take(5) + ":00") }.getOrNull()
    }

    // ---------- Lifecycle ----------
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        tokenManager = TokenManager(requireContext())
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        profileRepo = ProfileRepository(requireContext())
        sessionsRepo = SessionsByDateRepository(requireContext())

        loadAndSetUserName()
        refreshNowCardFromApi()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()
        loadAndSetUserName()
        refreshNowCardFromApi()
    }

    private fun loadAndSetUserName() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!profileRepo.hasToken()) return@launch
            when (val res = profileRepo.fetchMe()) {
                is com.example.herenow.data.MeResult.Success ->
                    binding.txtName.text = res.data.StudentFullName
                else -> Unit
            }
        }
    }

    // ---------- Main logic ----------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun refreshNowCardFromApi() {
        // Snapshot presence dari detail kelas (hindari unresolved reference di Home)
        data class PresenceSnapshot(
            val presenceId: Int? = null,
            val isVerified: Boolean = false,
            val isInCorrectLocation: Boolean = false,
            val isCorrectFace: Boolean = false
        )
        suspend fun safelyLoadPresenceSnapshot(classId: Int, sessionNumber: Int): PresenceSnapshot {
            val detailRepo = com.example.herenow.data.SessionsByClassRepository(requireContext())
            return when (val r = detailRepo.fetch(classId)) {
                is com.example.herenow.data.ClassDetailResult.Success -> {
                    val cs = r.sessions.firstOrNull { it.sessionNumber == sessionNumber }
                    val p = cs?.presences?.firstOrNull()
                    PresenceSnapshot(
                        presenceId = p?.presenceId,
                        isVerified = (p?.isVerified == 1),
                        isInCorrectLocation = (p?.isInCorrectLocation == 1),
                        isCorrectFace = (p?.isCorrectFace == 1)
                    )
                }
                else -> PresenceSnapshot()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val todayStr = LocalDate.now().toString()
            showLoading(true)

            when (val result = sessionsRepo.fetchNow(todayStr)) {
                is TodayNowResult.Success -> {
                    val s = result.session
                    val today = LocalDate.now()

                    // Validasi tanggal hari ini (robust)
                    val dateLd = parseLocalDateFlexible(s.sessionDate)
                    if (dateLd != today) {
                        showLoading(false); showEmpty(); return@launch
                    }

                    // Window tampil: [start - MINUTES_BEFORE_START, end)
                    val start = parseLocalTimeFlexible(s.shiftStart)
                    val end   = parseLocalTimeFlexible(s.shiftEnd)
                    val now   = LocalTime.now()
                    val inWindow = if (start != null && end != null) {
                        val windowStart = start.minusMinutes(MINUTES_BEFORE_START.toLong())
                        !now.isBefore(windowStart) && now.isBefore(end)
                    } else true // fallback tampil jika jam tidak bisa diparse

                    if (!inWindow) {
                        showLoading(false); showEmpty(); return@launch
                    }

                    // Dalam jendela → tampilkan kartu
                    showLoading(false)
                    binding.layoutHomeScheduleSection.visibility = View.VISIBLE
                    binding.animationEmpty.visibility = View.GONE
                    binding.txtEmpty?.visibility = View.GONE

                    // Header info
                    binding.tvCourseName.text     = s.courseName ?: "-"
                    binding.tvCourseCategory.text = s.courseCategory ?: "-"
                    binding.tvClassCode.text      = s.classCode ?: "Class #${s.classId}"
                    binding.tvRoomCode.text       = s.roomId ?: "-"

                    // Session chip & shift
                    binding.tvSession.text = s.sessionNumber.toString()

                    // Date & time
                    val prettyDate = (dateLd ?: today).format(dateFormatter)
                    val timeStr = buildString {
                        append((s.shiftStart ?: "").take(5).padEnd(5, '0'))
                        append(" - ")
                        append((s.shiftEnd ?: "").take(5).padEnd(5, '0'))
                        append(" WIB")
                    }
                    binding.tvDateAndDuration.text = "$prettyDate\n$timeStr"

                    // Status tombol (selaras DetailFragment)
                    val presence = safelyLoadPresenceSnapshot(s.classId, s.sessionNumber)
                    when {
                        presence.isVerified -> setAttendedUI()
                        presence.isInCorrectLocation && presence.isCorrectFace -> setWaitingVerificationUI()
                        else -> setAttendActionUI {
                            goToDetail(classId = s.classId, room = s.roomId)
                        }
                    }
                }

                is TodayNowResult.Empty -> {
                    showLoading(false); showEmpty()
                }
                is TodayNowResult.Unauthorized -> {
                    showLoading(false); showEmpty()
                }
                is TodayNowResult.Failure -> {
                    showLoading(false); showEmpty(message = result.message)
                }
            }
        }
    }

    // ---------- UI helpers ----------
    private fun showLoading(show: Boolean) {
        binding.progressBar?.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.layoutHomeScheduleSection.visibility = View.GONE
            binding.animationEmpty.visibility = View.GONE
            binding.txtEmpty?.visibility = View.GONE
        }
    }

    private fun showEmpty(message: String? = null) {
        binding.layoutHomeScheduleSection.visibility = View.GONE
        binding.animationEmpty.visibility = View.VISIBLE
        binding.txtEmpty?.visibility = View.VISIBLE
        if (!message.isNullOrBlank()) binding.txtEmpty?.text = message
    }

    private fun setAttendActionUI(onClick: () -> Unit) {
        binding.btnAttendance.apply {
            visibility = View.VISIBLE
            isEnabled = true
            text = getString(R.string.attendance)
            backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.yellow_orange)
            setOnClickListener { onClick() }
        }
    }

    private fun setAttendedUI() {
        binding.btnAttendance.apply {
            visibility = View.VISIBLE
            isEnabled = false
            text = getString(R.string.attended)
            backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark)
            setOnClickListener(null)
        }
    }

    private fun setWaitingVerificationUI() {
        binding.btnAttendance.apply {
            visibility = View.VISIBLE
            isEnabled = false
            text = "Waiting for Verification"
            backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.yellow_orange)
            setOnClickListener(null)
        }
    }

    private fun setNotAttendedUI() {
        binding.btnAttendance.apply {
            visibility = View.VISIBLE
            isEnabled = false
            text = getString(R.string.not_attended)
            backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_dark)
            setOnClickListener(null)
        }
    }

    // ---------- Navigation ----------
    private fun goToDetail(classId: Int, room: String?) {
        val detail = DetailFragment.newInstance(
            classId = classId,
            room = room ?: "-",
            code = "-",              // fallback; akan diisi ulang dari API
            title = "-",             // fallback
            type = "LEC",            // fallback
            instructor = "-",        // fallback
            creditsText = "0 Credits",
            totalSessions = 1,
            selectedSession = -1     // AUTO_PICK di DetailFragment
        ).apply {
            // Minta Detail otomatis mulai alur presensi
            arguments?.putBoolean("autoStartAttendance", true)
        }

        requireActivity().supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .replace(R.id.nav_host_fragment, detail)
            .addToBackStack(null)
            .commit()
    }

    // ---------- (Opsional) API helper: PUT Verified ----------
    private suspend fun putPresenceVerified(presenceId: Int, value: Boolean): Boolean {
        val token = tokenManager.getToken() ?: return false
        val url = "$BASE_URL/api/presence/$presenceId/verified"
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val json = """{"IsVerified":"${if (value) "1" else "0"}"}"""
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .put(body)
            .build()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
