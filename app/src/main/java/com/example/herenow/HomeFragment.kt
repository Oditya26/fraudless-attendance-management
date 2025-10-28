package com.example.herenow

import android.annotation.SuppressLint
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
import com.example.herenow.notify.NotificationHelper
import com.example.herenow.notify.ReminderScheduler
import com.facebook.shimmer.ShimmerFrameLayout
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
import com.example.herenow.data.local.PreferenceManager

class HomeFragment : Fragment() {

    // Tampilkan kartu mulai X menit sebelum jam mulai hingga sebelum jam selesai
    private val MINUTES_BEFORE_START = 15
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var profileRepo: ProfileRepository
    private lateinit var sessionsRepo: SessionsByDateRepository
    private lateinit var tokenManager: TokenManager
    private val BASE_URL = "http://202.10.44.214:8000"

    private var currentClassId: Int? = null

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
        preferenceManager = PreferenceManager(requireContext())

        val savedStatus = preferenceManager.getAttendanceStatus()
        if (savedStatus == "attended" || savedStatus == "waiting_verification") {
            currentClassId?.let { ReminderScheduler.cancelReminder(requireContext(), it) }
        }

        // Pastikan channel notifikasi siap
        NotificationHelper.ensureChannel(requireContext())

        showNameSkeleton(true)
        loadAndSetUserName()
        refreshNowCardFromApi()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()
        showNameSkeleton(true)
        loadAndSetUserName()
        refreshNowCardFromApi()
    }

    private fun loadAndSetUserName() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!profileRepo.hasToken()) {
                // Tidak ada token → biarkan skeleton (tidak set default nama)
                return@launch
            }
            when (val res = profileRepo.fetchMe()) {
                is com.example.herenow.data.MeResult.Success -> {
                    binding.txtName.text = res.data.StudentFullName
                    showNameSkeleton(false)
                    binding.txtName.visibility = View.VISIBLE
                }
                is com.example.herenow.data.MeResult.Unauthorized -> {
                    // Tetap skeleton, jangan menaruh default value
                    // (opsional) bisa set tooltip/tap-to-retry jika diperlukan
                }
                is com.example.herenow.data.MeResult.Failure -> {
                    // Tetap skeleton, jangan bocorkan detail error/IP
                    // (jika mau, tampilkan snackbar generik tanpa host/IP)
                }
                else -> {
                    // No-op, skeleton tetap on
                }
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
                    currentClassId = s.classId
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

                    ReminderScheduler.scheduleRepeatingReminder(
                        context = requireContext(),
                        classId = s.classId,
                        sessionNumber = s.sessionNumber,
                        className = s.courseName ?: "Class",
                        room = s.roomId ?: "-",
                        startDate = dateLd,
                        startTime = start,
                        endTime = end
                    )



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

                    // 1) Cek apakah sesi SUDAH MULAI
                    val hasStarted = start?.let { !now.isBefore(it) } ?: false

                    // 2) Jika BELUM mulai → sembunyikan tombol Attendance dan selesai
                    if (!hasStarted) {
                        setCheckSessionUI {
                            // buka detail tanpa auto start attendance
                            goToDetail(
                                classId = s.classId,
                                room = s.roomId,
                                sessionNumber = s.sessionNumber
                            )
                        }
                        return@launch
                    }


                    // 3) Jika SUDAH mulai → barulah tentukan status tombol
                    val presence = safelyLoadPresenceSnapshot(s.classId, s.sessionNumber)
                    when {
                        presence.isVerified -> setAttendedUI()
                        presence.isInCorrectLocation && presence.isCorrectFace -> setWaitingVerificationUI()
                        else -> setAttendActionUI {
                            goToDetail(classId = s.classId, room = s.roomId, sessionNumber = s.sessionNumber)
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
                    showLoading(false)
                    // Jangan lempar message asli (bisa ada URL/IP)
                    showEmpty(message = safeErrorMessage(result.message))
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
        // Hanya tampilkan pesan yang sudah disanitasi (dipanggil via safeErrorMessage)
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
        preferenceManager.setAttendanceStatus("pending")
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
        preferenceManager.setAttendanceStatus("attended")
        currentClassId?.let {
            ReminderScheduler.cancelReminder(requireContext(), it)
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
        preferenceManager.setAttendanceStatus("waiting_verification")

        currentClassId?.let {
            ReminderScheduler.cancelReminder(requireContext(), it)
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
    private fun goToDetail(classId: Int, room: String?, sessionNumber: Int) {
        val detail = DetailFragment.newInstance(
            classId = classId,
            room = room ?: "-",
            code = "-",
            title = "-",
            type = "LEC",
            instructor = "-",
            creditsText = "0 Credits",
            totalSessions = 1,
            selectedSession = sessionNumber // <— kirim eksplisit sesi yang diklik
        ).apply {
            arguments?.putBoolean("autoStartAttendance", false)
        }

        requireActivity().supportFragmentManager.beginTransaction()
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

    private fun hideAttendanceButton() {
        binding.btnAttendance.visibility = View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun setCheckSessionUI(onClick: () -> Unit) {
        binding.btnAttendance.apply {
            visibility = View.VISIBLE
            isEnabled = true
            text = "Check Session"
            backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.yellow_orange)
            setOnClickListener { onClick() }
        }
    }

    private fun showNameSkeleton(show: Boolean) {
        val shimmer = binding.shimmerName as? ShimmerFrameLayout
        if (show) {
            binding.txtName.visibility = View.GONE
            shimmer?.visibility = View.VISIBLE
            shimmer?.startShimmer()
        } else {
            shimmer?.stopShimmer()
            shimmer?.visibility = View.GONE
            // txtName visibility akan diatur oleh caller (sukses/fail)
        }
    }

    private fun safeErrorMessage(raw: String?): String {
        // Jika ada URL/IP/host, samarkan + gunakan pesan generik.
        if (raw.isNullOrBlank()) return "Unable to load data at this time."
        val leaky = listOf("http://", "https://", "://", "/", "Failed to connect", "timeout", "unreachable", "refused")
        val hasHostLike = Regex("""\b\d{1,3}(\.\d{1,3}){3}\b|[A-Za-z0-9.-]+\.[A-Za-z]{2,}""").containsMatchIn(raw)
        return if (leaky.any { raw.contains(it, ignoreCase = true) } || hasHostLike) {
            "Unable to connect. Check your internet connection."
        } else {
            raw
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
