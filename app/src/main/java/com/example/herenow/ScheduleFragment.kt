package com.example.herenow

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.herenow.data.ClassesRepository
import com.example.herenow.data.MyClassesResult
import com.example.herenow.data.ProfileRepository
import com.example.herenow.data.SessionsRepository
import com.example.herenow.data.SessionsResult
import com.example.herenow.data.local.TokenManager
import com.example.herenow.databinding.FragmentScheduleBinding
import com.example.herenow.model.Schedule
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import com.facebook.shimmer.ShimmerFrameLayout

class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private lateinit var profileRepo: ProfileRepository
    private lateinit var sessionsRepo: SessionsRepository
    private lateinit var classesRepo: ClassesRepository
    private lateinit var tokenManager: TokenManager

    @RequiresApi(Build.VERSION_CODES.O)
    private var selectedDate: LocalDate = LocalDate.now()

    // sessions dari /api/sessions/by-year-semester
    private var apiSchedules: List<Schedule> = emptyList()

    // meta dari /api/my-classes
    private var titleByCode: Map<String, String> = emptyMap()
    private var creditsByCode: Map<String, Int> = emptyMap()
    private var classIdByCourseCode: Map<String, Int> = emptyMap()
    private var classTypeByClassId: Map<Int, String> = emptyMap()
    private var instructorByClassId: Map<Int, String> = emptyMap()
    private var totalSessionsByClassId: Map<Int, Int> = emptyMap()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)

        profileRepo = ProfileRepository(requireContext())
        sessionsRepo = SessionsRepository(requireContext())
        classesRepo = ClassesRepository(requireContext())
        tokenManager = TokenManager(requireContext())

        // Divider antar item detail
        val divider = DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL)
        ContextCompat.getDrawable(requireContext(), R.drawable.divider_schedule)?.let {
            divider.setDrawable(it)
        }
        binding.scheduleRecyclerView.addItemDecoration(divider)
        binding.scheduleRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Header nama user
        showNameSkeleton(true)
        loadAndSetUserName()

        // UI kalender & dropdown
        setupMonthDropdown()
        setupCalendar()

        // Ambil meta my-classes dulu → lanjut fetch sessions
        fetchMyClassesMeta()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        showNameSkeleton(true)
        loadAndSetUserName()
    }

    /** Ambil nama user dari /api/me */
    private fun loadAndSetUserName() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!profileRepo.hasToken()) return@launch
            when (val res = profileRepo.fetchMe()) {
                is com.example.herenow.data.MeResult.Success -> {
                    binding.txtName.text = res.data.StudentFullName
                    showNameSkeleton(false)
                }
                is com.example.herenow.data.MeResult.Unauthorized -> {
                    // tetap skeleton; jika perlu redirect login di tempat lain
                }
                is com.example.herenow.data.MeResult.Failure -> {
                    // tetap skeleton; jangan tampilkan toast/error yang berpotensi berisi IP
                }
            }
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
            binding.txtName.visibility = View.VISIBLE
        }
    }

    // ================= Ambil META dari /api/my-classes =================

    @RequiresApi(Build.VERSION_CODES.O)
    private fun fetchMyClassesMeta() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val res = classesRepo.fetchMyEnrollments()) {
                is MyClassesResult.Success -> {
                    // peta meta untuk render detail dan navigasi
                    titleByCode = res.enrollments.associate { it.courseCode to it.courseTitle }
                    creditsByCode = res.enrollments.associate { it.courseCode to (it.credits ?: 0) }
                    classIdByCourseCode = res.enrollments
                        .mapNotNull { e -> e.classId?.let { id -> e.courseCode to id } }
                        .toMap()

                    classTypeByClassId = res.enrollments
                        .mapNotNull { e -> e.classId?.let { id -> id to e.classType } }
                        .toMap()

                    instructorByClassId = res.enrollments
                        .mapNotNull { e -> e.classId?.let { id -> id to e.instructor } }
                        .toMap()

                    totalSessionsByClassId = res.enrollments
                        .mapNotNull { e -> e.classId?.let { id -> id to e.totalSessions } }
                        .toMap()


                    // setelah meta siap → ambil sesi sesuai selectedDate
                    fetchSessionsFor(selectedDate)
                }
                is MyClassesResult.Unauthorized -> {
                    tokenManager.clear()
                    startActivity(
                        android.content.Intent(requireContext(), LoginActivity::class.java).apply {
                            addFlags(
                                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }
                    )
                    requireActivity().finish()
                }
                is MyClassesResult.Failure -> {
                    // defaultkan kosong agar aman, tetap coba fetch sessions
                    titleByCode = emptyMap()
                    creditsByCode = emptyMap()
                    classIdByCourseCode = emptyMap()
                    classTypeByClassId = emptyMap()
                    instructorByClassId = emptyMap()
                    totalSessionsByClassId = emptyMap()
                    fetchSessionsFor(selectedDate)
                }
            }
        }
    }

    // ================= Calendar =================

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupCalendar() {
        setMonthHeaderText()
        setCalendarRecyclerView()

        binding.btnPrevMonth.setOnClickListener {
            selectedDate = selectedDate.minusMonths(1)
            setMonthHeaderText()
            syncDropdownToSelectedDate()
            setCalendarRecyclerView()
            fetchSessionsFor(selectedDate)
        }

        binding.btnNextMonth.setOnClickListener {
            selectedDate = selectedDate.plusMonths(1)
            setMonthHeaderText()
            syncDropdownToSelectedDate()
            setCalendarRecyclerView()
            fetchSessionsFor(selectedDate)
        }
    }

    /** Header teks di atas kalender mengikuti selectedDate */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setMonthHeaderText() {
        val fmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("en", "US"))
        binding.spinnerMonth.setText(selectedDate.format(fmt), false)
    }

    /** Ubah bulan-tahun dari kode, sinkronkan semua & refetch data */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateCalendarTo(year: Int, month: Int) {
        selectedDate = selectedDate.withYear(year).withMonth(month).withDayOfMonth(1)
        setMonthHeaderText()
        syncDropdownToSelectedDate()
        setCalendarRecyclerView()
        fetchSessionsFor(selectedDate)
    }

    /** Ambil data sessions berdasarkan (year, semester) */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun fetchSessionsFor(date: LocalDate) {
        viewLifecycleOwner.lifecycleScope.launch {
            val (year, semester) = determineYearSemester(date)
            binding.progressBar.visibility = View.VISIBLE

            when (val res = sessionsRepo.fetch(year, semester)) {
                is SessionsResult.Success -> {
                    apiSchedules = res.schedules
                    binding.progressBar.visibility = View.GONE
                    setCalendarRecyclerView()
                    val initial = initialSelectedDateForCalendar() // <— ganti
                    showDetailsFor(initial)
                }
                is SessionsResult.Unauthorized -> {
                    binding.progressBar.visibility = View.GONE
                    tokenManager.clear()
                    startActivity(
                        android.content.Intent(requireContext(), LoginActivity::class.java).apply {
                            addFlags(
                                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }
                    )
                    requireActivity().finish()
                }
                is SessionsResult.Failure -> {
                    binding.progressBar.visibility = View.GONE
                    apiSchedules = emptyList()
                    setCalendarRecyclerView()
                    binding.layoutScheduleDetail.visibility = View.GONE
                    binding.scheduleRecyclerView.adapter = null
                }
            }
        }
    }

    /**
     * Semester rule:
     * Odd (1): September–Januari
     * Even (2): Februari–Juni
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun determineYearSemester(date: LocalDate): Pair<Int, Int> {
        val month = date.monthValue
        val semester = when (month) {
            in 9..12, 1 -> 1 // Odd
            in 2..6 -> 2    // Even
            else -> 2       // default (Juli–Agustus)
        }
        val year = if (month == 1) date.year - 1 else date.year
        return year to semester
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setCalendarRecyclerView() {
        val daysInMonth = daysInMonthArray(selectedDate)
        val initial = initialSelectedDateForCalendar() // <— ganti

        val adapter = CalendarAdapter(
            days = daysInMonth,
            monthDate = selectedDate,
            schedules = apiSchedules,
            onDateClick = { _, daySchedules ->
                if (daySchedules.isEmpty()) {
                    binding.layoutScheduleDetail.visibility = View.GONE
                    binding.scheduleRecyclerView.adapter = null
                } else {
                    binding.layoutScheduleDetail.visibility = View.VISIBLE
                    val sorted = daySchedules.sortedWith(
                        compareBy(
                            { LocalDateTime.of(it.date, parseStartTime(it.time)) },
                            { it.session }
                        )
                    )
                    binding.scheduleRecyclerView.adapter = ScheduleDetailAdapter(
                        schedules = sorted,
                        resolveCourseTitle = { code -> titleByCode[code] ?: code },
                        resolveCourseCategory = { s ->
                            val byId = s.classId?.let { classTypeByClassId[it] }
                            val byCode = classIdByCourseCode[s.courseCode]?.let { classTypeByClassId[it] }
                            byId ?: byCode ?: "-"
                        }
                    ) { s -> navigateToDetail(s) }
                }
            },
            initialSelectedDate = initial // <— tetap dipass ke adapter
        )

        binding.calendarRecyclerView.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.calendarRecyclerView.adapter = adapter
    }



    @RequiresApi(Build.VERSION_CODES.O)
    private fun showDetailsFor(date: LocalDate) {
        val daySchedules = apiSchedules.filter { it.date == date }
        if (daySchedules.isEmpty()) {
            binding.layoutScheduleDetail.visibility = View.GONE
            binding.scheduleRecyclerView.adapter = null
        } else {
            binding.layoutScheduleDetail.visibility = View.VISIBLE

            val sorted = daySchedules.sortedWith(
                compareBy(
                    { LocalDateTime.of(it.date, parseStartTime(it.time)) },
                    { it.session }
                )
            )

            binding.scheduleRecyclerView.adapter = ScheduleDetailAdapter(
                schedules = sorted,
                resolveCourseTitle = { code -> titleByCode[code] ?: code },
                resolveCourseCategory = { s ->
                    val byId = s.classId?.let { classTypeByClassId[it] }
                    val byCode = classIdByCourseCode[s.courseCode]?.let { classTypeByClassId[it] }
                    byId ?: byCode ?: "-"
                }
            ) { s -> navigateToDetail(s) }

        }
    }

    /** Navigasi ke Detail dengan meta dari /api/my-classes */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun navigateToDetail(s: Schedule) {
        // Ambil classId dari item yang dipilih; fallback ke peta lama jika null
        val classId = s.classId ?: (classIdByCourseCode[s.courseCode] ?: 0)

        val title       = titleByCode[s.courseCode] ?: s.courseCode
        val creditsText = "${creditsByCode[s.courseCode] ?: 0} Credits"
        val classType   = classTypeByClassId[classId] ?: "LEC"
        val instructor  = instructorByClassId[classId] ?: "TBA"
        val totalSess   = totalSessionsByClassId[classId] ?: 13

        val detail = DetailFragment.newInstance(
            classId = classId,
            room = s.room,
            code = s.courseCode,
            title = titleByCode[s.courseCode] ?: s.courseCode,
            type = classTypeByClassId[classId] ?: "-",
            instructor = instructorByClassId[classId] ?: "TBA",
            creditsText = "${creditsByCode[s.courseCode] ?: 0} Credits",
            totalSessions = totalSessionsByClassId[classId] ?: 13,
            selectedSession = s.session
        )

        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left,  R.anim.slide_out_right
            )
            .replace(R.id.nav_host_fragment, detail)
            .addToBackStack(null)
            .commit()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun daysInMonthArray(date: LocalDate): List<Int?> {
        val list = mutableListOf<Int?>()
        val ym = YearMonth.from(date)
        val days = ym.lengthOfMonth()
        val first = date.withDayOfMonth(1)
        val dow = first.dayOfWeek.value % 7 // Sunday-first
        repeat(dow) { list.add(null) }
        for (d in 1..days) list.add(d)
        return list
    }

    private val TIME_PATTERN = Regex("""\b(\d{1,2}):(\d{2})""")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseStartTime(timeStr: String): LocalTime {
        val match = TIME_PATTERN.find(timeStr.lowercase(Locale.ROOT)) ?: return LocalTime.MIN
        val (h, m) = match.destructured
        return LocalTime.of(h.toInt(), m.toInt())
    }

    // ================= Dropdown Bulan–Tahun (AutoCompleteTextView) =================
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupMonthDropdown() {
        val items = generateMonthYearList() // "April 2025", "March 2025", ...
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner_dropdown,  // isi dropdown: teks hitam, bg putih
            items
        )

        val actv: AppCompatAutoCompleteTextView = binding.spinnerMonth
        actv.setAdapter(adapter)

        // batasi tinggi dropdown agar tidak memenuhi layar
        actv.dropDownHeight = resources.getDimensionPixelSize(R.dimen.dropdown_max_height)
        actv.dropDownWidth  = LayoutParams.WRAP_CONTENT

        // sinkron label awal
        syncDropdownToSelectedDate()

        actv.setOnItemClickListener { _, _, position, _ ->
            val selected = items[position]
            val parts = selected.split(" ")
            if (parts.size == 2) {
                val month = monthNameToNumber(parts[0])
                val year  = parts[1].toIntOrNull() ?: return@setOnItemClickListener
                updateCalendarTo(year, month)
            }
        }

        // tap field → show dropdown
        actv.setOnClickListener { actv.showDropDown() }
    }

    /** Sesuaikan teks dropdown dengan selectedDate */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun syncDropdownToSelectedDate() {
        val fmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("en", "US"))
        val label = selectedDate.format(fmt)
        val actv: AppCompatAutoCompleteTextView = binding.spinnerMonth
        if (actv.text.toString() != label) {
            actv.setText(label, false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun generateMonthYearList(): List<String> {
        val now = LocalDate.now()
        val currentYear = now.year
        val months = listOf(
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        )
        val result = mutableListOf<String>()
        for (y in currentYear downTo 1990) {
            for (m in 12 downTo 1) {
                result.add("${months[m - 1]} $y")
            }
        }
        return result
    }

    private fun monthNameToNumber(name: String): Int {
        val months = listOf(
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        )
        val idx = months.indexOfFirst { it.equals(name, ignoreCase = true) }
        return (idx + 1).coerceAtLeast(1)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun firstDateWithScheduleInSelectedMonthOrSelected(): LocalDate {
        val inMonth = apiSchedules
            .map { it.date }
            .filter { it.year == selectedDate.year && it.month == selectedDate.month }
            .sorted()
        return inMonth.firstOrNull() ?: selectedDate
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initialSelectedDateForCalendar(): LocalDate {
        val today = LocalDate.now()

        // Jika bulan yang sedang ditampilkan = bulan hari ini → pilih hari ini
        if (selectedDate.year == today.year && selectedDate.month == today.month) {
            return today
        }

        // Kalau bukan bulan ini: pilih tanggal pertama yang punya jadwal di bulan tsb, kalau tidak ada → hari pertama bulan tsb
        val inMonth = apiSchedules
            .map { it.date }
            .filter { it.year == selectedDate.year && it.month == selectedDate.month }
            .sorted()
        return inMonth.firstOrNull() ?: selectedDate.withDayOfMonth(1)
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
