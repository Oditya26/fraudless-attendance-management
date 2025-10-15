@file:Suppress("DEPRECATION")

package com.example.herenow

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import com.example.herenow.data.ClassDetailResult
import com.example.herenow.data.LookupsRepository
import com.example.herenow.data.LookupsResult
import com.example.herenow.data.ProfileRepository
import com.example.herenow.data.RoomRepository
import com.example.herenow.data.RoomResult
import com.example.herenow.data.SessionsByClassRepository
import com.example.herenow.data.local.TokenManager
import com.example.herenow.data.remote.sessions.ClassMetaDto
import com.example.herenow.data.remote.sessions.ClassSessionDto
import com.example.herenow.data.remote.sessions.PresenceDto
import com.example.herenow.databinding.FragmentDetailBinding
import com.example.herenow.util.LocationChecker
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.zxing.client.android.Intents
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.location.Location
import android.provider.Settings
import android.view.Gravity
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay

@Suppress("DEPRECATION")
class DetailFragment : Fragment() {
    // --- di class DetailFragment ---

    private fun ShimmerFrameLayout?.on() { this?.visibility = View.VISIBLE; this?.startShimmer() }
    private fun ShimmerFrameLayout?.off() { this?.stopShimmer(); this?.visibility = View.GONE }


    @RequiresApi(Build.VERSION_CODES.O)
    private data class SessionTiming(
        val isFuture: Boolean,
        val isOngoing: Boolean,
        val isPast: Boolean
    )

    private var autoStartAttendance: Boolean = false

    // NEW: util sederhana untuk menyimpan progres lokal per presenceId
    private object LocalPresenceStore {
        private const val PREF = "presence_local_flags"
        private fun prefs(ctx: android.content.Context) =
            ctx.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)

        fun set(ctx: android.content.Context, presenceId: Int, key: String, value: Boolean) {
            prefs(ctx).edit().putBoolean("p_${presenceId}_$key", value).apply()
        }
        fun get(ctx: android.content.Context, presenceId: Int, key: String): Boolean =
            prefs(ctx).getBoolean("p_${presenceId}_$key", false)

        fun clearAllFor(ctx: android.content.Context, presenceId: Int) {
            val p = prefs(ctx)
            p.edit()
                .remove("p_${presenceId}_loc")
                .remove("p_${presenceId}_qr")
                .remove("p_${presenceId}_face")
                .apply()
        }
    }

    // RESET flow: hapus flag lokal & arahkan user ulang ke share location
    @RequiresApi(Build.VERSION_CODES.O)
    private fun resetFlowAndRestart(sessionNo: Int = selectedSession) {
        val id = getSelectedPresenceId(sessionNo) ?: return
        LocalPresenceStore.clearAllFor(requireContext(), id)
        showCustomToast("Verification failed. Restarting from location.")
        setAttendActionUI { startLocationCheckThenAttendance(sessionNo) }
    }



    private lateinit var tokenManager: TokenManager
    private val BASE_URL = "http://202.10.44.214:8000"

    private var studentId: String? = null

    // --- Location / Lookups ---
    private lateinit var lookupsRepo: LookupsRepository
    private lateinit var fused: com.google.android.gms.location.FusedLocationProviderClient
    private var pendingSessionForLocationCheck: Int? = null

    // --- SnapHelper guard ---
    private var sessionSnapHelper: androidx.recyclerview.widget.SnapHelper? = null
    private var sessionsDecorationAdded = false

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var classRepo: SessionsByClassRepository
    private lateinit var profileRepo: ProfileRepository
    private lateinit var roomRepo: RoomRepository

    // --- Camera/QR ---
    private var pendingSessionForQrCheck: Int? = null

    companion object {
        private const val AUTO_PICK = -1
        const val ARG_ROOM_CODE = "roomCode"
        const val ARG_CLASS_ID = "classId"
        const val ARG_ROOM = "room"
        const val ARG_CODE = "code"
        const val ARG_TITLE = "title"
        const val ARG_TYPE = "type"
        const val ARG_INSTRUCTOR = "instructor"
        const val ARG_CREDITS = "creditsText"
        const val ARG_TOTAL_SESSIONS = "totalSessions"
        const val ARG_SELECTED_SESSION = "selectedSession"

        fun newInstance(
            classId: Int,
            room: String,
            code: String,
            title: String,
            type: String,
            instructor: String,
            creditsText: String,
            totalSessions: Int,
            selectedSession: Int = AUTO_PICK
        ) = DetailFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_CLASS_ID, classId)
                putString(ARG_ROOM, room)
                putString(ARG_CODE, code)
                putString(ARG_TITLE, title)
                putString(ARG_TYPE, type)
                putString(ARG_INSTRUCTOR, instructor)
                putString(ARG_CREDITS, creditsText)
                putInt(ARG_TOTAL_SESSIONS, totalSessions)
                putInt(ARG_SELECTED_SESSION, selectedSession)
            }
        }
    }

    // Activity Result launcher untuk scanner QR
    @RequiresApi(Build.VERSION_CODES.O)
    private val qrScanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data: Intent? = result.data
            val scannedText = data?.getStringExtra(Intents.Scan.RESULT)
            if (scannedText.isNullOrBlank()) {
                showCustomToast("QR scan canceled.")
                setAttendActionUI { startLocationCheckThenAttendance(selectedSession) }
                return@registerForActivityResult
            }

            val roomIdForSelected = getRoomIdForSession(selectedSession)
            if (roomIdForSelected.isBlank()) {
                showCustomToast("Room code unavailable.")
                setAttendActionUI { startLocationCheckThenAttendance(selectedSession) }
                return@registerForActivityResult
            }
            // Lanjut verifikasi QR
            verifyRoomCode(roomIdForSelected, scannedText)
        }

    // Selfie (bitmap) → upload sebagai file
    @RequiresApi(Build.VERSION_CODES.O)
    private val selfieLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp == null) {
            showCustomToast("Failed to capture photo.")
            setAttendActionUI { startLocationCheckThenAttendance(selectedSession) }
            return@registerForActivityResult
        }
        uploadSelfieAndVerify(bmp)
    }

    // Permission launcher - LOCATION
    @RequiresApi(Build.VERSION_CODES.O)
    private val requestFineLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val sessionNo = pendingSessionForLocationCheck
            pendingSessionForLocationCheck = null
            if (granted && sessionNo != null) {
                obtainLocationAndValidate(sessionNo)
            } else {
                showCustomToast("Location permission denied.")
                setAttendActionUI { startLocationCheckThenAttendance(selectedSession) }
            }
        }

    // CAMERA permission for QR
    @RequiresApi(Build.VERSION_CODES.O)
    private val requestCameraForQr =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val sessionNo = pendingSessionForQrCheck
            pendingSessionForQrCheck = null
            if (granted && sessionNo != null) startQrScannerForSession(sessionNo)
            else {
                showCustomToast("Camera permission denied.")
                setAttendActionUI { startLocationCheckThenAttendance(selectedSession) }
            }
        }

    // CAMERA permission for Selfie
    @RequiresApi(Build.VERSION_CODES.O)
    private val requestCameraForSelfie =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                if (isAdded) selfieLauncher.launch(null)
            } else {
                showCustomToast("Camera permission denied.")
                setAttendActionUI { startLocationCheckThenAttendance(selectedSession) }
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private val ID_DATE_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale("id", "ID"))

    // Args / state
    private var classId: Int = 0
    private var selectedSession: Int = AUTO_PICK
    private var totalSessions: Int = 1
    private lateinit var courseCode: String
    private lateinit var courseTitle: String
    private var roomArg: String = "-"
    private var classTypeArg: String = "LEC"
    private var instructorArg: String = "-"
    private var creditsArg: String = "0 Credits"

    // Data API
    private var meta: ClassMetaDto? = null
    private var sessions: List<ClassSessionDto> = emptyList()

    // Optional views
    private val progressBar get() = binding.root.findViewById<View?>(R.id.progressBar)
    private val tvDateAndDuration get() = binding.root.findViewById<TextView?>(R.id.tvDateAndDuration)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)

        classRepo = SessionsByClassRepository(requireContext())
        profileRepo = ProfileRepository(requireContext())
        roomRepo = RoomRepository(requireContext())
        tokenManager = TokenManager(requireContext())

        lookupsRepo = LookupsRepository(requireContext())
        fused = LocationServices.getFusedLocationProviderClient(requireContext())

        // Args
        classId = arguments?.getInt(ARG_CLASS_ID) ?: 0
        selectedSession = arguments?.getInt(ARG_SELECTED_SESSION) ?: AUTO_PICK
        totalSessions = arguments?.getInt(ARG_TOTAL_SESSIONS) ?: 1
        courseCode = arguments?.getString(ARG_CODE) ?: "-"
        courseTitle = arguments?.getString(ARG_TITLE) ?: courseCode
        roomArg = arguments?.getString(ARG_ROOM) ?: "-"
        classTypeArg = arguments?.getString(ARG_TYPE) ?: "LEC"
        instructorArg = arguments?.getString(ARG_INSTRUCTOR) ?: "-"
        creditsArg = arguments?.getString(ARG_CREDITS) ?: "0 Credits"

        autoStartAttendance = arguments?.getBoolean("autoStartAttendance") == true


        if (classId <= 0) {
            Toast.makeText(requireContext(), "Invalid ClassId.", Toast.LENGTH_SHORT).show()
            return binding.root
        }

        showNameSkeleton(true)
        showHeaderSkeleton(true)
        showSessionInfoSkeleton(true)

        loadAndSetUserName()
        fetchClassDetail()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        loadAndSetUserName()
    }

    private fun loadAndSetUserName() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!profileRepo.hasToken()) return@launch
            when (val res = profileRepo.fetchMe()) {
                is com.example.herenow.data.MeResult.Success -> {
                    binding.txtName?.text = res.data.StudentFullName
                    studentId = res.data.StudentId
                    showNameSkeleton(false) // tampilkan nama
                }
                else -> {
                    // gagal → biarkan skeleton nama tetap ON (jangan isi default/jangan toast IP)
                    showNameSkeleton(true)
                }
            }
        }
    }


    // ---------- Presence helpers ----------
    private fun getSelectedPresence(sessionNo: Int = selectedSession): PresenceDto? {
        val s = sessions.firstOrNull { it.sessionNumber == sessionNo } ?: return null
        return s.presences?.firstOrNull()
    }

    private fun getSelectedPresenceId(sessionNo: Int = selectedSession): Int? =
        getSelectedPresence(sessionNo)?.presenceId

    private fun isLocationDone(sessionNo: Int = selectedSession) =
        getSelectedPresence(sessionNo)?.isInCorrectLocation == 1

    private fun isQrDone(sessionNo: Int = selectedSession) =
        getSelectedPresence(sessionNo)?.isVerified == 1

    private fun isFaceDone(sessionNo: Int = selectedSession) =
        getSelectedPresence(sessionNo)?.isCorrectFace == 1

    private fun markLocalPresence(
        sessionNo: Int = selectedSession,
        loc: Int? = null,
        qr: Int? = null,
        face: Int? = null
    ) {
        sessions = sessions.map { s ->
            if (s.sessionNumber != sessionNo) return@map s
            val p = s.presences?.firstOrNull() ?: return@map s
            val np = p.copy(
                isInCorrectLocation = loc  ?: p.isInCorrectLocation,
                isVerified          = qr   ?: p.isVerified,
                isCorrectFace       = face ?: p.isCorrectFace
            )
            s.copy(presences = listOf(np))
        }
    }

    private suspend fun putPresenceField(
        presenceId: Int,
        pathSuffix: String,               // "location" | "verified" | "face"
        fieldName: String,                // "IsInCorrectLocation" | "IsVerified" | "IsCorrectFace"
        value: String                     // "1" atau "0"
    ): Boolean {
        val token = tokenManager.getToken() ?: return false
        val url = "$BASE_URL/api/presence/$presenceId/$pathSuffix"

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val json = """{"$fieldName":"$value"}"""
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .put(body)
            .build()

        return withContext(Dispatchers.IO) {
            runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
        }
    }

    private suspend fun updatePresenceLocation(presenceId: Int, ok: Boolean) =
        putPresenceField(presenceId, "location", "IsInCorrectLocation", if (ok) "1" else "0")

    private suspend fun updatePresenceVerified(presenceId: Int, ok: Boolean) =
        putPresenceField(presenceId, "verified", "IsVerified", if (ok) "1" else "0")

    private suspend fun updatePresenceFace(presenceId: Int, ok: Boolean) =
        putPresenceField(presenceId, "face", "IsCorrectFace", if (ok) "1" else "0")

    // ========= API detail =========
    @RequiresApi(Build.VERSION_CODES.O)
    private fun fetchClassDetail() {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(true)
            when (val res = classRepo.fetch(classId)) {
                is ClassDetailResult.Success -> {
                    showLoading(false)
                    meta = res.clazz
                    sessions = res.sessions.sortedBy { it.sessionNumber }

                    bindHeader(meta)
                    showHeaderSkeleton(false)

                    if (selectedSession == AUTO_PICK) {
                        selectedSession = pickBestSession(sessions)
                    }

                    clearLocalAndMemoryForSession(selectedSession)
                    setupSessionsRecycler(meta, sessions)

                    updateHeaderRoomForSession(selectedSession)
                    showSessionInfoSkeleton(false)

                    updateAttendanceUIForSession(selectedSession)
                }
                is ClassDetailResult.Unauthorized -> {
                    showLoading(false)
                    // Tetap skeleton; jangan bocorkan detail error
                    showHeaderSkeleton(true)
                    showSessionInfoSkeleton(true)
                    setupSessionsRecycler(null, emptyList())
                    binding.btnAttendance.visibility = View.GONE
                }
                is ClassDetailResult.Failure -> {
                    showLoading(false)
                    // Tetap skeleton; jangan tampilkan res.message
                    showHeaderSkeleton(true)
                    showSessionInfoSkeleton(true)
                    setupSessionsRecycler(null, emptyList())
                    binding.btnAttendance.visibility = View.GONE
                }
            }
        }
    }



    private fun showLoading(show: Boolean) = withBinding {
        progressBar?.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ========= Header =========
    private fun bindHeaderFallback() {
        binding.txtRoom.text = roomArg
        binding.txtCode.text = courseCode
        binding.txtTitle.text = courseTitle
        binding.txtClassType.text = classTypeArg
        binding.txtInstructor.text = instructorArg
        binding.txtCredits.text = creditsArg
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun bindHeader(m: ClassMetaDto?) {
        if (m == null) return
        binding.txtCode.text = m.courseId ?: m.classCode ?: courseCode
        binding.txtTitle.text = m.courseName ?: courseTitle
        binding.txtClassType.text = m.courseCategory ?: classTypeArg
        binding.txtInstructor.text = m.lecturerFullName ?: instructorArg
        val creditVal = m.credit ?: creditsArg.filter { it.isDigit() }.toIntOrNull() ?: 0
        binding.txtCredits.text = "$creditVal Credits"
// Do NOT set txtRoom or tvDateAndDuration here; they are session-dependent and
// are updated by updateHeaderForSession(selectedSession)
    }

    // ========= Recycler sesi =========
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupSessionsRecycler(m: ClassMetaDto?, apiSessions: List<ClassSessionDto>) {
        val totalFromMeta = m?.numberOfSession
        val totalFromData = apiSessions.maxOfOrNull { it.sessionNumber }
        val total = totalFromMeta ?: totalFromData ?: totalSessions
        val sessionNumbers = (1..total).toList()

        val sessionAdapter = SessionAdapter(
            sessionNumbers,
            initiallySelected = selectedSession
        ) { chosen ->
            selectedSession = chosen
            clearLocalAndMemoryForSession(chosen)
            updateHeaderRoomForSession(chosen)
            updateAttendanceUIForSession(chosen)
        }

        binding.rvSessions.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvSessions.adapter = sessionAdapter

        if (!sessionsDecorationAdded) {
            val spacing = resources.getDimensionPixelSize(R.dimen.session_spacing)
            val edge = resources.getDimensionPixelSize(R.dimen.session_edge_spacing)
            binding.rvSessions.addItemDecoration(
                com.example.herenow.ui.common.HorizontalSpacingItemDecoration(spacing, edge)
            )
            sessionsDecorationAdded = true
        }

        sessionSnapHelper?.attachToRecyclerView(null)
        binding.rvSessions.onFlingListener = null
        sessionSnapHelper = LinearSnapHelper().also { it.attachToRecyclerView(binding.rvSessions) }
        centerOnSelectedSession()
    }

    private fun centerOnSelectedSession() {
        val rv = binding.rvSessions
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val index = (selectedSession - 1).coerceAtLeast(0)

        // Pastikan ukuran & layout sudah jadi dulu
        rv.post {
            // Bikin item terlihat dulu
            lm.scrollToPositionWithOffset(index, 0)

            // Lalu gunakan SnapHelper biar item-nya ke-snap di tengah
            rv.post {
                val snap = sessionSnapHelper ?: return@post
                val snapView = snap.findSnapView(lm) ?: run {
                    // Kalau belum ada view tersnap, arahkan smooth ke index dulu
                    rv.smoothScrollToPosition(index)
                    return@post
                }
                val dist = snap.calculateDistanceToFinalSnap(lm, snapView)
                if (dist != null) rv.smoothScrollBy(dist[0], dist[1])
            }
        }
    }


    // ========= Auto-pick sesi =========
    @RequiresApi(Build.VERSION_CODES.O)
    private fun pickBestSession(apiSessions: List<ClassSessionDto>): Int {
        if (apiSessions.isEmpty()) return 1

        val today = LocalDate.now()
        val now = LocalTime.now()

        data class S(val num: Int, val date: LocalDate, val start: LocalTime, val end: LocalTime)

        val parsed = apiSessions.mapNotNull { s ->
            try {
                S(
                    num = s.sessionNumber,
                    date = LocalDate.parse(s.sessionDate),
                    start = LocalTime.parse(s.shiftStart),
                    end = LocalTime.parse(s.shiftEnd)
                )
            } catch (_: Exception) {
                null
            }
        }.sortedWith(compareBy({ it.date }, { it.start }))

        if (parsed.isEmpty()) return 1

        parsed.firstOrNull { it.date.isEqual(today) && !now.isBefore(it.start) && now.isBefore(it.end) }
            ?.let { return it.num }

        parsed.filter { it.date.isBefore(today) || (it.date.isEqual(today) && !now.isBefore(it.end)) }
            .maxWithOrNull(compareBy({ it.date }, { it.end }))
            ?.let { return it.num }

        parsed.filter { it.date.isAfter(today) || (it.date.isEqual(today) && now.isBefore(it.start)) }
            .minWithOrNull(compareBy({ it.date }, { it.start }))
            ?.let { return it.num }

        return parsed.first().num
    }

    // ========= Aturan tombol & CEK LOKASI =========
    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateAttendanceUIForSession(sessionNo: Int) {
        val s = sessions.firstOrNull { it.sessionNumber == sessionNo }
        if (s == null) {
            binding.btnAttendance.visibility = View.GONE
            tvDateAndDuration?.text = null
            return
        }

        // Selalu refresh header untuk sesi terpilih
        updateHeaderForSession(sessionNo)

        val p = s.presences?.firstOrNull()
        val loc = (p?.isInCorrectLocation ?: 0) == 1
        val face = (p?.isCorrectFace ?: 0) == 1
        val verified = (p?.isVerified ?: 0) == 1

        val T = timingOf(s)

        // === RULE #4: Sesi mendatang → tombol disembunyikan ===
        if (T.isFuture) {
            hideAttendanceButton()      // <-- DIPANGGIL DI SINI
            return
        }

        // === RULE #2: Verified = 1 → Attended (hijau) terlepas kondisi lain ===
        if (verified) {
            setAttendedUI()
            return
        }

        // === RULE #1: Verified = 0 tetapi loc=1 dan face=1 → Waiting for Verification (kuning) ===
        if (!verified && loc && face) {
            setWaitingVerificationUI()
            return
        }

        // === RULE #3: Semua 0 dan sesi lampau → Not Attended (merah) ===
        if (!loc && !face && !verified && T.isPast) {
            setNotAttendedUI()
            return
        }

        // Default: sesi sedang berlangsung / lampau tapi belum lengkap → tampilkan aksi Attendance
        setAttendActionUI { startLocationCheckThenAttendance(sessionNo) }
    }


    // NEW
    private fun setWaitingVerificationUI() = withBinding {
        btnAttendance.apply {
            visibility = View.VISIBLE
            isEnabled = false
            text = "Waiting for Verification"
            backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.yellow_orange)
            setOnClickListener(null)
        }
    }

    // NEW: auto-verify ketika sesi sudah lewat
    @RequiresApi(Build.VERSION_CODES.O)
    private fun autoVerifyIfNeeded(sessionNo: Int, s: ClassSessionDto) {
        val p = s.presences?.firstOrNull() ?: return
        if (p.isVerified == 1) return
        val presenceId = p.presenceId

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = updatePresenceVerified(presenceId, true)
            if (ok) {
                // update salinan lokal di memori agar UI langsung berubah
                markLocalPresence(sessionNo, qr = 1 /* kita pakai 'qr' field lokal sbg dummy, boleh diabaikan */, face = null, loc = null)
                // refresh UI setelah verified
                setAttendedUI()
            }
        }
    }


    private fun setAttendActionUI(onClick: () -> Unit) = withBinding {
        btnAttendance.apply {
            visibility = View.VISIBLE
            isEnabled = true
            text = getString(R.string.attendance)
            backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.yellow_orange)
            setOnClickListener { onClick() }
        }
    }

    private fun setAttendedUI() = withBinding {
        btnAttendance.apply {
            visibility = View.VISIBLE
            isEnabled = false
            text = getString(R.string.attended)
            backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark)
            setOnClickListener(null)
        }
    }

    private fun setNotAttendedUI() = withBinding {
        btnAttendance.apply {
            visibility = View.VISIBLE
            isEnabled = false
            text = getString(R.string.not_attended)
            backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_dark)
            setOnClickListener(null)
        }
    }

    // ---------- Permission helpers ----------
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestLocationPermission() {
        requestFineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    // ---------- Alur lokasi -> QR ----------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startLocationCheckThenAttendance(sessionNo: Int) {
        clearLocalAndMemoryForSession(sessionNo)
        showCustomToast("Checking location...")
        setWaitingUI("Waiting for Location")

        val s = sessions.firstOrNull { it.sessionNumber == sessionNo }
        if (hasFullyAttendedServer(s)) {
            setAttendedUI()
            showCustomToast("Attendance already recorded.")
            return
        }

        // Jika lokasi sudah OK, lompat ke langkah berikutnya
        if (isLocationDone(sessionNo)) {
            showCustomToast("Location OK, continue.")
            if (isQrDone(sessionNo)) {
                if (isFaceDone(sessionNo)) {
                    showCustomToast("All steps complete.")
                    setAttendedUI()
                } else {
                    showCustomToast("Proceed to face verification.")
                    startSelfieFlow()
                }
            } else {
                showCustomToast("Proceed to scan QR.")
                startQrFlowAfterLocation(sessionNo)
            }
            return
        }

        pendingSessionForLocationCheck = sessionNo

        if (!hasLocationPermission()) {
            showCustomToast("Location permission required. Enable GPS.")
            requestLocationPermission()
            return
        }

        obtainLocationAndValidate(sessionNo)
    }

    // CHANGED: hanya set lokal; API /location dipanggil saat finalizeIfAllLocalOk()
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    private fun obtainLocationAndValidate(sessionNo: Int) {
        val presenceId = getSelectedPresenceId(sessionNo)
        if (presenceId == null) {
            showCustomToast("Invalid PresenceId.")
            setAttendActionUI { startLocationCheckThenAttendance(sessionNo) }
            return
        }

        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc == null) {
                        showCustomToast("Unable to read GPS.")
                        setAttendActionUI { startLocationCheckThenAttendance(sessionNo) }
                        return@addOnSuccessListener
                    }
                    if (isMockLocation(loc)) {
                        showCustomToast("Fake GPS detected")
                        // hentikan proses & kembalikan tombol aksi agar user bisa coba lagi setelah mematikan mock
                        setAttendActionUI { startLocationCheckThenAttendance(sessionNo) }
                        return@addOnSuccessListener
                    }
                    val userLat = loc.latitude
                    val userLon = loc.longitude

                    viewLifecycleOwner.lifecycleScope.launch {
                        when (val locRes = lookupsRepo.fetchByCategory("BINUS_MALANG_LOCATION")) {
                            is LookupsResult.Success -> {
                                val (targetLat, targetLon) = parseBinusLatLon(locRes)
                                if (targetLat == null || targetLon == null) {
                                    showCustomToast("Campus location mismatch.")
                                    setAttendActionUI { startLocationCheckThenAttendance(sessionNo) }
                                    return@launch
                                }

                                when (val offRes = lookupsRepo.fetchByCategory("LOCATION_OFFSET_IN_METER")) {
                                    is LookupsResult.Success -> {
                                        val offsetMeters = parseOffsetMeters(offRes) ?: 250.0
                                        val within = LocationChecker.isWithinOffset(
                                            currentLat = userLat, currentLon = userLon,
                                            targetLat = targetLat, targetLon = targetLon,
                                            offsetMeters = offsetMeters
                                        )

                                        toastMatch("Location", within)
                                        // SIMPAN LOKAL
                                        LocalPresenceStore.set(requireContext(), presenceId, "loc", within)

                                        if (within) {
                                            setWaitingUI("Waiting for Scan QR Code Class")
                                            startQrFlowAfterLocation(sessionNo)
                                        } else {
                                            // === NEW
                                            resetFlowAndRestart(sessionNo)
                                            // === END NEW
                                        }
                                    }
                                    else -> {
                                        showCustomToast("Failed to load location radius.")
                                        setAttendActionUI { startLocationCheckThenAttendance(sessionNo) }
                                    }
                                }
                            }
                            else -> {
                                showCustomToast("Failed to load campus location config.")
                                setAttendActionUI { startLocationCheckThenAttendance(sessionNo) }
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    showCustomToast("Failed to get device location.")
                    setAttendActionUI { startLocationCheckThenAttendance(sessionNo) }
                }
        } catch (_: SecurityException) {
            showCustomToast("Location permission unavailable.")
            setAttendActionUI { startLocationCheckThenAttendance(sessionNo) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startQrFlowAfterLocation(sessionNo: Int) {
        setWaitingUI("Waiting for Scan QR Code Class")

        // Jika QR sudah OK, lanjut selfie/selesai
        if (isQrDone(sessionNo)) {
            if (isFaceDone(sessionNo)) setAttendedUI() else startSelfieFlow()
            return
        }

        val roomId = getRoomIdForSession(sessionNo)
        if (roomId.isBlank()) {
            showCustomToast("Room Code is unavailable.")
            setAttendActionUI { startLocationCheckThenAttendance(sessionNo) }
            return
        }

        pendingSessionForQrCheck = sessionNo
        if (!hasCameraPermission()) { requestCameraForQr.launch(Manifest.permission.CAMERA); return }
        startQrScannerForSession(sessionNo)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startQrScannerForSession(@Suppress("UNUSED_PARAMETER") sessionNo: Int) {
        val integrator = IntentIntegrator.forSupportFragment(this).apply {
            setCaptureActivity(com.example.herenow.ui.qr.PortraitCaptureActivity::class.java)
            setOrientationLocked(true)
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            setPrompt("Point the camera at the class QR code")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
        }
        val intent = integrator.createScanIntent()
        qrScanLauncher.launch(intent)
    }

    // CHANGED: hanya set lokal qr=true/false, TIDAK memanggil updatePresenceVerified()
    @RequiresApi(Build.VERSION_CODES.O)
    private fun verifyRoomCode(roomId: String, scanned: String) {
        val scannedN = normalizeCode(scanned)
        val presenceId = getSelectedPresenceId()
        if (presenceId == null) {
            showCustomToast("PresenceId is not found.")
            setAttendActionUI { startLocationCheckThenAttendance(selectedSession) }
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            when (val res = roomRepo.fetchRoom(roomId)) {
                is RoomResult.Success -> {
                    val expectedN = normalizeCode(res.data.RoomCode)
                    val isMatch = scannedN == expectedN
                    toastMatch("QR", isMatch)
                    showCustomToast("Take photo of your face")
                    delay(2500)
                    // SIMPAN LOKAL
                    LocalPresenceStore.set(requireContext(), presenceId, "qr", isMatch)

                    if (isMatch) {
                        setWaitingUI("Waiting for Face Recognition")
                        if (isFaceOkLocal()) {
                            // kalau tiga langkah sudah OK, finalize
                            finalizeIfAllLocalOk(selectedSession)
                        } else {
                            startSelfieFlow()
                        }
                    } else {
                        // === NEW
                        resetFlowAndRestart(selectedSession)
                        // === END NEW
                    }
                }
                else -> {
                    showCustomToast("Failed to load room data.")
                    setAttendActionUI { startLocationCheckThenAttendance(selectedSession) }
                }
            }
        }
    }


    // ---------- UI Helpers ----------
    private fun showLocationError(@Suppress("UNUSED_PARAMETER") msg: String) { /* no-op */ }
    private fun showNotWithinDialog(@Suppress("UNUSED_PARAMETER") offsetMeters: Double) { /* no-op */ }

    /** Parser untuk lat-lon dari response lookups 'BINUS_MALANG_LOCATION' */
    private fun parseBinusLatLon(res: LookupsResult.Success): Pair<Double?, Double?> {
        var lat: Double? = null
        var lon: Double? = null
        res.items.forEach { item ->
            val v = item.lookupvalue?.trim()
            if (!v.isNullOrEmpty()) {
                val direct = v.toDoubleOrNull()
                if (direct != null) {
                    if (item.lookupdescription?.contains("Latitude", ignoreCase = true) == true) lat = direct
                    if (item.lookupdescription?.contains("Long", ignoreCase = true) == true) lon = direct
                } else {
                    val parts = v.replace(",", " ").split(" ").filter { it.isNotBlank() }
                    if (parts.size >= 2) {
                        val p0 = parts[0].toDoubleOrNull()
                        val p1 = parts[1].toDoubleOrNull()
                        if (p0 != null && p1 != null) {
                            if (lat == null) lat = p0
                            if (lon == null) lon = p1
                        }
                    }
                }
            }
        }
        return lat to lon
    }

    /** Parser untuk offset meter dari 'LOCATION_OFFSET_IN_METER' */
    private fun parseOffsetMeters(res: LookupsResult.Success): Double? {
        val v = res.items.firstOrNull()?.lookupvalue?.trim() ?: return null
        return v.toDoubleOrNull()
    }

    private fun normalizeCode(raw: String?): String {
        if (raw == null) return ""
        var s = raw.trim()
        s = Normalizer.normalize(s, Normalizer.Form.NFKC)
        val invisibles = setOf('\uFEFF', '\u200B', '\u200C', '\u200D', '\u2060')
        s = s.filter { ch -> !Character.isISOControl(ch) && ch !in invisibles }
        return s
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateHeaderRoomForSession(sessionNo: Int) {
        updateHeaderForSession(sessionNo)
    }

    private fun getRoomIdForSession(sessionNo: Int): String {
        return sessions.firstOrNull { it.sessionNumber == sessionNo }?.roomId.orEmpty()
    }

    private inline fun withBinding(block: FragmentDetailBinding.() -> Unit) {
        val b = _binding ?: return
        block(b)
    }

    private inline fun runIfViewActive(block: () -> Unit) {
        if (isAdded && _binding != null) block()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startSelfieFlow() {
        if (isFaceDone()) { setAttendedUI(); return }
        setWaitingUI("Waiting for Face Recognition")
        if (!hasCameraPermission()) {
            requestCameraForSelfie.launch(Manifest.permission.CAMERA)
            return
        }
        selfieLauncher.launch(null)
    }

    // CHANGED: setelah match, set lokal lalu finalize (PUT location & face).
    @RequiresApi(Build.VERSION_CODES.O)
    private fun uploadSelfieAndVerify(bitmap: android.graphics.Bitmap) {
        val sid = studentId.orEmpty()
        if (sid.isBlank()) {
            showCustomToast("StudentId unavailable.")
            setAttendActionUI { startLocationCheckThenAttendance(selectedSession) }
            return
        }
        val presenceId = getSelectedPresenceId()
        if (presenceId == null) {
            showCustomToast("PresenceId not found.")
            setAttendActionUI { startLocationCheckThenAttendance(selectedSession) }
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(true)
            val ctx = context ?: return@launch

            val file = runCatching {
                val out = java.io.File(ctx.cacheDir, "selfie_${System.currentTimeMillis()}.jpg")
                var q = 90
                while (true) {
                    java.io.FileOutputStream(out).use { fos ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, q, fos)
                    }
                    if (out.length() <= 500_000 || q <= 60) break
                    q -= 5
                }
                out
            }.getOrElse {
                showLoading(false)
                showCustomToast("Failed to prepare photo file.")
                setAttendActionUI { startLocationCheckThenAttendance(selectedSession) }
                return@launch
            }

            val client = OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("photo", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                .addFormDataPart("studentId", sid)
                .build()

            val req = Request.Builder()
                .url("http://202.10.44.214:5050/recognition/verify")
                .post(multipart)
                .build()

            val (ok, bodyStr) = withContext(Dispatchers.IO) {
                runCatching {
                    client.newCall(req).execute().use { resp ->
                        resp.isSuccessful to (resp.body?.string().orEmpty())
                    }
                }.getOrElse { false to (it.message ?: "Network error") }
            }

            showLoading(false)

            if (!ok) {
                showCustomToast("Face verification failed.")
                // tandai lokal face=false
                LocalPresenceStore.set(requireContext(), presenceId, "face", false)
                updateAttendanceUIForSession(selectedSession)
                setAttendActionUI { startLocationCheckThenAttendance(selectedSession) }
                return@launch
            }

            val json = runCatching { org.json.JSONObject(bodyStr) }.getOrNull()
            val isMatch = json?.optBoolean("isMatch", false) == true
            val similarity = json?.optString("similarity").orEmpty()
            val apiStudentId = json?.optString("studentId").orEmpty()

            // SIMPAN LOKAL face
            LocalPresenceStore.set(requireContext(), presenceId, "face", isMatch)
            toastMatch("Face", isMatch)

            AlertDialog.Builder(ctx)
                .setTitle("Face Match")
                .setMessage(
                    buildString {
                        append("StudentId: $apiStudentId\n")
                        append("Similarity: $similarity\n")
                        append("Result: ${if (isMatch) "Match ✅" else "Not Match ❌"}")
                    }
                )
                .setPositiveButton("OK") { _, _ ->
                    runIfViewActive {
                        if (isMatch) {
                            setWaitingUI("Syncing Attendance...")
                            // TIGA LOKAL OK? Kirim PUT location & face
                            finalizeIfAllLocalOk(selectedSession)
                        } else {
                            // === NEW
                            resetFlowAndRestart(selectedSession)
                            // === END NEW
                        }
                    }
                }
                .show()
        }
    }


    private fun hasFullyAttendedServer(s: ClassSessionDto?): Boolean {
        if (s == null) return false
        return s.presences.orEmpty().any { p ->
            p.isInCorrectLocation == 1 && p.isCorrectFace == 1
        }
    }

    private fun setWaitingUI(label: String) = withBinding {
        btnAttendance.apply {
            visibility = View.VISIBLE
            isEnabled = false
            text = label
            backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.yellow_orange)
            setOnClickListener(null)
        }
    }

    // ---------- Toast helpers (EN, short) ----------
    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else activity?.runOnUiThread { block() }
    }

    // gunakan untuk semua toast
    private fun safeToast(msg: String) {
        if (!isAdded) return
        val appCtx = requireContext().applicationContext
        runOnMain { Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show() }
    }

    // ringkas "match/mismatch"
    private fun toastMatch(feature: String, ok: Boolean) {
        showCustomToast("$feature: " + if (ok) "OK" else "Not OK")
    }


    // NEW
    private fun isLocOkLocal(sessionNo: Int = selectedSession): Boolean {
        val id = getSelectedPresenceId(sessionNo) ?: return false
        return LocalPresenceStore.get(requireContext(), id, "loc")
    }
    private fun isQrOkLocal(sessionNo: Int = selectedSession): Boolean {
        val id = getSelectedPresenceId(sessionNo) ?: return false
        return LocalPresenceStore.get(requireContext(), id, "qr")
    }
    private fun isFaceOkLocal(sessionNo: Int = selectedSession): Boolean {
        val id = getSelectedPresenceId(sessionNo) ?: return false
        return LocalPresenceStore.get(requireContext(), id, "face")
    }

    // Kirim PUT /location dan /face hanya ketika loc && qr && face == true
    @RequiresApi(Build.VERSION_CODES.O)
    private fun finalizeIfAllLocalOk(sessionNo: Int = selectedSession) {
        val presenceId = getSelectedPresenceId(sessionNo) ?: return
        if (!isLocOkLocal(sessionNo) || !isQrOkLocal(sessionNo) || !isFaceOkLocal(sessionNo)) {
            // Tidak lengkap → ulangi dari awal
            resetFlowAndRestart(sessionNo)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(true)
            val okLocation = updatePresenceLocation(presenceId, true)
            val okFace     = updatePresenceFace(presenceId, true)
            showLoading(false)

            if (okLocation && okFace) {
                // bersihkan cache lokal agar tidak nyangkut
                LocalPresenceStore.clearAllFor(requireContext(), presenceId)
                // Server belum tentu verified → tombol "Waiting for Verification" dulu
                val verified = getSelectedPresence(sessionNo)?.isVerified == 1
                if (verified) setAttendedUI() else setWaitingVerificationUI()
            } else {
                showCustomToast("Sync failed. Restarting.")
                resetFlowAndRestart(sessionNo)
            }
        }
    }

    private fun clearLocalAndMemoryForSession(sessionNo: Int) {
        val pid = getSelectedPresenceId(sessionNo) ?: return
        LocalPresenceStore.clearAllFor(requireContext(), pid)
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateHeaderForSession(sessionNo: Int) = withBinding {
        val s = sessions.firstOrNull { it.sessionNumber == sessionNo }
        val room = s?.roomId?.takeIf { it.isNotBlank() } ?: "-"
        txtRoom.text = room

        val dateLbl = try {
            val d = s?.sessionDate?.let { java.time.LocalDate.parse(it) }
            val start = s?.shiftStart?.orEmpty()
            val end = s?.shiftEnd?.orEmpty()
            if (d != null && start?.isNotBlank() == true && end?.isNotBlank() == true) {
                "${d.format(ID_DATE_FMT)}\n${start.take(5)} - ${end.take(5)} WIB"
            } else if (start?.isNotBlank() == true && end?.isNotBlank() == true) {
                "${start.take(5)} - ${end.take(5)} WIB"
            } else null
        } catch (_: Exception) { null }

        tvDateAndDuration?.text = dateLbl
        showSessionInfoSkeleton(false) // <- room & date sudah diisi
    }


    private fun hideAttendanceButton() = withBinding {
        btnAttendance.visibility = View.GONE
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun timingOf(s: ClassSessionDto?): SessionTiming {
        val today = LocalDate.now()
        val now = LocalTime.now()

        val d = runCatching { LocalDate.parse(s?.sessionDate) }.getOrNull()
        val start = runCatching { LocalTime.parse(s?.shiftStart) }.getOrNull() ?: LocalTime.MIN
        val end = runCatching { LocalTime.parse(s?.shiftEnd) }.getOrNull() ?: LocalTime.MAX

        val isFuture = when {
            d == null -> false
            d.isAfter(today) -> true
            d.isEqual(today) && now.isBefore(start) -> true
            else -> false
        }
        val isPast = when {
            d == null -> false
            d.isBefore(today) -> true
            d.isEqual(today) && !now.isBefore(end) -> true
            else -> false
        }
        val isOngoing = !isFuture && !isPast
        return SessionTiming(isFuture, isOngoing, isPast)
    }

    private fun isMockLocation(location: Location?): Boolean {
        if (location == null) return false

        // 1) API 18+ punya flag mock provider yang andal
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (location.isFromMockProvider) return true
        }

        // 2) Legacy fallback: setting global "Allow mock locations" (pra-Android M)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            val mockSetting = Settings.Secure.getString(
                requireContext().contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION
            )
            if (mockSetting != null && mockSetting != "0") return true
        }

        return false
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun showCustomToast(
        message: String,
        isLong: Boolean = false,
        gravity: Int = Gravity.BOTTOM,
        yOffsetDp: Int = 72
    ) {
        // pastikan di main thread
        val show = {
            val inflater = layoutInflater
            // parent = null supaya layout params dari root diambil dari xml
            val layout = inflater.inflate(R.layout.custom_toast, null)
            val tv = layout.findViewById<TextView>(R.id.tvMessage)
            tv.text = message

            // gunakan context fragment (bukan applicationContext)
            val toast = Toast(requireContext())
            toast.duration = if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            toast.view = layout
            toast.setGravity(gravity, 0, dpToPx(yOffsetDp))
            toast.show()
        }

        if (Looper.myLooper() == Looper.getMainLooper()) show() else {
            activity?.runOnUiThread { show() }
        }
    }

    private fun showNameSkeleton(show: Boolean) {
        val sh = binding.shimmerName as? ShimmerFrameLayout
        if (show) { binding.txtName?.visibility = View.GONE; sh.on() }
        else { sh.off(); binding.txtName?.visibility = View.VISIBLE }
    }

    private fun showHeaderSkeleton(show: Boolean) {
        val shCode = binding.root.findViewById<ShimmerFrameLayout>(R.id.shimmerCode)
        val shTitle = binding.root.findViewById<ShimmerFrameLayout>(R.id.shimmerTitle)
        val shType = binding.root.findViewById<ShimmerFrameLayout>(R.id.shimmerClassType)
        val shIns  = binding.root.findViewById<ShimmerFrameLayout>(R.id.shimmerInstructor)
        val shCred = binding.root.findViewById<ShimmerFrameLayout>(R.id.shimmerCredits)

        if (show) {
            shCode.on(); shTitle.on(); shType.on(); shIns.on(); shCred.on()
            binding.txtCode.visibility = View.GONE
            binding.txtTitle.visibility = View.GONE
            binding.txtClassType.visibility = View.GONE
            binding.txtInstructor.visibility = View.GONE
            binding.txtCredits.visibility = View.GONE
        } else {
            shCode.off(); shTitle.off(); shType.off(); shIns.off(); shCred.off()
            binding.txtCode.visibility = View.VISIBLE
            binding.txtTitle.visibility = View.VISIBLE
            binding.txtClassType.visibility = View.VISIBLE
            binding.txtInstructor.visibility = View.VISIBLE
            binding.txtCredits.visibility = View.VISIBLE
        }
    }

    private fun showSessionInfoSkeleton(show: Boolean) {
        val shRoom = binding.root.findViewById<ShimmerFrameLayout>(R.id.shimmerRoom)
        val shDate = binding.root.findViewById<ShimmerFrameLayout>(R.id.shimmerDate)

        if (show) {
            shRoom.on(); shDate.on()
            binding.txtRoom.visibility = View.GONE
            binding.tvDateAndDuration?.visibility = View.GONE
        } else {
            shRoom.off(); shDate.off()
            binding.txtRoom.visibility = View.VISIBLE
            binding.tvDateAndDuration?.visibility = View.VISIBLE
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        sessionSnapHelper?.attachToRecyclerView(null)
    }
}
